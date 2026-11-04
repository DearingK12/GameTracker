package dev.gametracker;

public record PlaySession(int id, String gameName, long startedAt, Long endedAt,
                          double durationSeconds, String source) {
    public String getGameName() {
        return gameName;
    }

    public String getStartedText() {
        return java.time.Instant.ofEpochMilli(startedAt)
                .atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a"));
    }

    public String getDurationText() {
        long minutes = (long) (durationSeconds / 60);
        if (minutes < 60) return minutes + " min";
        return String.format("%d hr %02d min", minutes / 60, minutes % 60);
    }

    public String getSource() {
        return source;
    }
}
