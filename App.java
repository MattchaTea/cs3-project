package rrhs.computerscience.spotifyplayer;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Hello world!
 *
 */
public class App {

    public static String spotifyAccessToken;

    public static void main(String[] args) {

        String message;
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("What is your spotify access token: ");
            spotifyAccessToken = scanner.nextLine();
            System.out.print("What music are you feeling? ");
            message = scanner.nextLine();
        }
        List<String> gptOutput = chatGPT(message);
        // Prints out a response to the question.
        String playlistId = createPlaylist("Personalized playlist of " + message, "description3");
        List<String> trackIds = automate(gptOutput);
        addSong(playlistId, trackIds);

        try {
            String url = "https://open.spotify.com/playlist/" + playlistId;
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(new URI(url));
            } else {
                System.out.println("BROWSE action not supported on this platform.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static List<String> chatGPT(String message) {
        String url = "https://api.openai.com/v1/chat/completions";
        String apiKey = "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"; // API key goes here
        String model = "gpt-4.1-mini"; // current model of chatgpt api

        try {
            // Create the HTTP POST request
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            con.setRequestProperty("Content-Type", "application/json");

            // Build the request body
            String prompt = "Recommend 10 songs that match the following user input: " + message
                    + ". Format the response exactly as: Song name - Artist, with each pair separated only by commas. "
                    + "Do not include quotes, numbers, bullet points, line breaks, or any extra formatting.";

            String body = "{"
                    + "\"model\": \"" + model + "\","
                    + "\"messages\": [{"
                    + "\"role\": \"user\","
                    + "\"content\": \"" + prompt + "\""
                    + "}]"
                    + "}";
            System.out.println(body);
            con.setDoOutput(true);
            OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream());
            writer.write(body);
            writer.flush();
            writer.close();

            // Get the response
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // returns the extracted contents of the response.
            String responseBody = extractContentFromResponse(response.toString());
            String[] songArray = responseBody.split(", ");
            System.out.println(Arrays.toString(songArray));
            return Arrays.asList(songArray);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // This method extracts the response expected from chatgpt and returns it.
    public static String extractContentFromResponse(String response) {
        JsonObject obj = JsonParser.parseString(response).getAsJsonObject();
        JsonArray choices = obj.getAsJsonArray("choices");
        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        String content = message.get("content").getAsString();
        return content;

    }

    public static String post(String url, String json) {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Authorization", "Bearer " + spotifyAccessToken);
            httpPost.setHeader("Content-type", "application/json");
            StringEntity stringEntity = new StringEntity(json);
            httpPost.setEntity(stringEntity);

            System.out.println("Executing request " + httpPost.getRequestLine());

            // Create a custom response handler
            ResponseHandler<String> responseHandler = response -> {
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    HttpEntity entity = response.getEntity();
                    return entity != null ? EntityUtils.toString(entity) : null;
                } else {
                    throw new ClientProtocolException("Unexpected response status: " + response);
                }
            };
            String responseBody = httpclient.execute(httpPost, responseHandler);
            System.out.println("----------------------------------------");
            System.out.println(responseBody);
            return responseBody;
        } catch (Exception e) {
            System.err.println(e);
        }
        return null;
    }

    public static String get(String url) {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            //HTTP GET method
            HttpGet httpget = new HttpGet(url);
            httpget.setHeader("Authorization", "Bearer " + spotifyAccessToken);
            System.out.println("Executing request " + httpget.getRequestLine());

            // Create a custom response handler
            ResponseHandler< String> responseHandler = response -> {
                int status = response.getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    HttpEntity entity = response.getEntity();
                    return entity != null ? EntityUtils.toString(entity) : null;
                } else {
                    throw new ClientProtocolException("Unexpected response status: " + response);
                }
            };
            String responseBody = httpclient.execute(httpget, responseHandler);
            System.out.println("----------------------------------------");
            System.out.println(responseBody);
            return responseBody;
        } catch (Exception e) {
            System.err.println(e);
        }
        return null;
    }

    public static String createPlaylist(String name, String description) {
        String jsonRequest = "{\r\n"
                + //
                "    \"name\": \"" + name + "\",\r\n"
                + //
                "    \"description\": \"" + description + "\",\r\n"
                + //
                "    \"public\": true\r\n"
                + //
                "}";

        String jsonResponse = post("https://api.spotify.com/v1/users/medauhuk2lgaxcycu3d1kf27f/playlists", jsonRequest);
        JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
        String playlistId = jsonObject.get("id").getAsString();
        return playlistId;

    }

    public static String searchSong(String userInput) {

        String url = "https://api.spotify.com/v1/search";
        try {
            URI uri = new URIBuilder(url)
                    .addParameter("q", userInput)
                    .addParameter("type", "track")
                    .addParameter("market", "US")
                    .addParameter("limit", "1")
                    .build();
            String json = get(uri.toString());
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray items = root.getAsJsonObject("tracks").getAsJsonArray("items");

            if (items.size() > 0) {
                JsonObject track = items.get(0).getAsJsonObject();
                String trackId = track.get("id").getAsString();
                System.out.println("Track ID: " + trackId);
                return trackId;
            } else {
                System.out.println("No tracks found.");
                return null;
            }
        } catch (URISyntaxException ex) {
            System.err.println(ex);
            return null;
        }

    }

    public static String addSong(String playlistId, List<String> trackIds) {
        String trackUris = trackIds.stream().map(trackId -> "\"spotify:track:" + trackId + "\"").collect(Collectors.joining(","));
        String jsonRequest = "{\r\n"
                + //
                "    \"uris\": [" + trackUris + "],\r\n"
                + //
                "\"position\": 0"
                + "}";
        System.out.println(jsonRequest);
        return post("https://api.spotify.com/v1/playlists/" + playlistId + "/tracks", jsonRequest);
    }

    public static List<String> automate(List<String> songs) {
        List<String> trackIds = new ArrayList<>();
        for (int i = 0; i < songs.size(); i++) {
            trackIds.add(searchSong(songs.get(i)));
        }
        System.out.println(trackIds);
        return trackIds;

    }
}
