package top.fifthlight.armorstand.updatelogextractor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UpdateLogExtractor {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("Usage: UpdateLogExtractor <version name> <output file> <input file>...");
            return;
        }
        var versionName = args[0];
        var outputPath = Path.of(args[1]);

        try (var writer = Files.newBufferedWriter(outputPath)) {
            for (var i = 2; i < args.length; i++) {
                var updateLogPath = Path.of(args[i]);

                try (var reader = Files.newBufferedReader(updateLogPath)) {
                    var logContentBuilder = new StringBuilder();
                    var foundVersion = false;
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.trim().startsWith("## " + versionName)) {
                            foundVersion = true;
                            continue;
                        }

                        if (foundVersion) {
                            if (line.trim().startsWith("## ")) {
                                break;
                            }
                            logContentBuilder.append(line).append(System.lineSeparator());
                        }
                    }

                    var logText = logContentBuilder.toString().trim();
                    if (!logText.isEmpty()) {
                        if (i > 2) {
                            writer.newLine();
                            writer.write("---");
                            writer.newLine();
                            writer.newLine();
                        }
                        writer.write(logText);
                        writer.newLine();
                    }
                }
            }
        }
    }
}
