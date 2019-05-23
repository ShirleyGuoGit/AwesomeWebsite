package external;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

import entity.Item;
//import entity.Item.*;
import entity.TickerMasterObject;

public class TicketMasterAPI {
	private static final String URL = "https://app.ticketmaster.com/discovery/v2/events.json";
	private static final String DEFAULT_KEYWORD = ""; // no restriction， 全局搜索
	private static final String API_KEY = "SGnMnH6ZIg5SBrpNdbDEvG7RGGGspR9r";
	
	public List<Item> search(double lat, double lon, String keyword) {
		if (keyword == null) {
			keyword = DEFAULT_KEYWORD;
		}
		
		try {
			keyword = URLEncoder.encode(keyword, "UTF-8"); //"Rick Sun" => "Rick%20Sun", dealing with space 
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		
		//GeoHash
		String geoHash = GeoHash.encodeGeohash(lat, lon, 8);
		String query = String.format("apikey=%s&geoPoint=%s&keyword=%s&radius=%s&size=%s&sort=%s", 
									API_KEY, geoHash, keyword, 100, 30, "random");
		
//		String query = String.format("apikey=%s&latlong=%s,%s&keyword=%s&radius=%s&size=%s&sort=%s", 
//				                      API_KEY, lat, lon, keyword, 50, 30, "date,desc");
		
		String url = URL + "?" + query;
		
		try {
			HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setRequestMethod("GET");
			
			int responseCode = connection.getResponseCode();
			System.out.println("Sending request to url: " + url);
			System.out.println("Response code: " + responseCode);
			
			if (responseCode != 200) {
				return new ArrayList<>();
			}
			// buffer reader 是将数据分块放在内存，然后readLine 去一行行读取
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String line;
			StringBuilder response = new StringBuilder();
			
			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
			reader.close();
			JSONObject objJSON = new JSONObject(response.toString());
			
			if (!objJSON.isNull("_embedded")) {
				JSONObject embedded = objJSON.getJSONObject("_embedded");
			    for (int i = 0; i < embedded.getJSONArray("events").length(); ++i) {
			        JSONObject event = embedded.getJSONArray("events").getJSONObject(i);
			        ObjectMapper objectMapper = new ObjectMapper();
			        TickerMasterObject obj = objectMapper.readValue(event.toString(), TickerMasterObject.class);
			        
			        // build Item object from the TickerMasterObject instance 
			     }
				return getItemList(embedded.getJSONArray("events"));
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return new ArrayList<>();
	}
	
	private void queryAPI(double lat, double lon) {
		List<Item> events = search(lat, lon, null);

		for (Item event : events) {
			System.out.println(event.toJSONObject());
		}
		
//		JSONArray events = search(lat, lon, null);
//
//		try {
//		    for (int i = 0; i < events.length(); ++i) {
//		       JSONObject event = events.getJSONObject(i);
//		       System.out.println(event.toString(2));// 将Json object 打印出来，每个空两个space
//		    }
//		} 
//		catch (Exception e) {
////	    catch (IOException e || JSONException e) { 
//	          e.printStackTrace();
//		}
	}

	/**
	 * Helper methods
	 */
	private String getAddress(JSONObject event) throws JSONException {
		if (!event.isNull("_embedded")) {
			JSONObject embedded = event.getJSONObject("_embedded");
			if (!embedded.isNull("venues")) {
				JSONArray venues = embedded.getJSONArray("venues");
				for (int i = 0; i < venues.length(); ++i) {
					JSONObject venue = venues.getJSONObject(i);
					StringBuilder builder = new StringBuilder();
					if (!venue.isNull("address")) {
						JSONObject address = venue.getJSONObject("address");
						if (!address.isNull("line1")) {
							builder.append(address.getString("line1"));
						}
						
						if (!address.isNull("line2")) {
							builder.append(",");
							builder.append(address.getString("line2"));
						}
						
						if (!address.isNull("line3")) {
							builder.append(",");
							builder.append(address.getString("line3"));
						}
					}
					
					if (!venue.isNull("city")) {
						JSONObject city = venue.getJSONObject("city");
						builder.append(",");
						builder.append(city.getString("name"));
					}
					
					String result = builder.toString();
					if (!result.isEmpty()) {
						return result;
					}
				}
			}
		}
		return "";
		
	}

	private String getImageUrl(JSONObject event) throws JSONException {
		if (!event.isNull("images")) {
			JSONArray array = event.getJSONArray("images");
			for (int i = 0; i < array.length(); i++) {
				JSONObject image = array.getJSONObject(i);
				if (!image.isNull("url")) {
					return image.getString("url");
				}
			}
		}
		return "";
	}
	
	// Convert JSONArray to a list of item objects.
	// event
	private List<Item> getItemList(JSONArray events) throws JSONException {
		List<Item> itemList = new ArrayList<>();
		for (int i = 0; i < events.length(); ++i) {
			JSONObject event = events.getJSONObject(i);
			//ItemBuilder builder = new ItemBuilder();
			Item.ItemBuilder builder = new Item.ItemBuilder();
			if (!event.isNull("id")) {
				builder.setItemId(event.getString("id"));
			}
			if (!event.isNull("name")) {
				builder.setName(event.getString("name"));
			}
			if (!event.isNull("url")) {
				builder.setUrl(event.getString("url"));
			}
			if (!event.isNull("distance")) {
				builder.setDistance(event.getDouble("distance"));
			}
			if (!event.isNull("rating")) {
				builder.setDistance(event.getDouble("rating"));
			}
			builder.setAddress(getAddress(event))
			       .setCategories(getCategories(event))
			       .setImageUrl(getImageUrl(event));
			
			itemList.add(builder.build());
		}
		return itemList;
	}

	private Set<String> getCategories(JSONObject event) throws JSONException {
		
		Set<String> categories = new HashSet<>();
		if (!event.isNull("classifications")) {
			JSONArray classifications = event.getJSONArray("classifications");
			for (int i = 0; i < classifications.length(); ++i) {
				JSONObject classification = classifications.getJSONObject(i);
				if (!classification.isNull("segment")) {
					JSONObject segment = classification.getJSONObject("segment");
					if (!segment.isNull("name")) {
						categories.add(segment.getString("name"));
					}
				}
			}
		}
		return categories;
	}

	/**
	 * Main entry for sample TicketMaster API requests.
	 */
	public static void main(String[] args) {
		TicketMasterAPI tmApi = new TicketMasterAPI();
		// Mountain View, CA
		// tmApi.queryAPI(37.38, -122.08);
		// London, UK
		// tmApi.queryAPI(51.503364, -0.12);
		// Houston, TX
		// tmApi.queryAPI(29.682684, -95.295410);
		//Seattle, WA
		tmApi.queryAPI(47.608013, -122.335167);
	}
}

