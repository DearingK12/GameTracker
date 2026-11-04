package dev.gametracker;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class SteamImporter {
    public static int importGames(String apiKey, String steamId, Database db)
            throws IOException, InterruptedException {
        if (apiKey.isBlank() || steamId.isBlank()) {
            throw new IllegalArgumentException("Both API key and SteamID64 are required.");
        }
        String url = "https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/?key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "&steamid=" + URLEncoder.encode(steamId, StandardCharsets.UTF_8)
                + "&include_appinfo=true&include_played_free_games=true";
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Steam returned HTTP " + response.statusCode());
        }
        JsonNode root = new ObjectMapper().readTree(response.body()).path("response").path("games");
        if (!root.isArray()) {
            throw new IllegalStateException("No game list returned. Check profile privacy and Steam credentials.");
        }
        int count = 0;
        for (JsonNode game : root) {
            String name = game.path("name").asText("");
            if (name.isBlank()) continue;
            double hours = game.path("playtime_forever").asDouble(0) / 60.0;
            db.upsertSteam(name, hours);
            count++;
        }
        return count;
    }
}
