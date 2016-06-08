package io.vedder.utils.webvideodownloader;

import java.util.List;
import java.util.stream.Collectors;

public class Main {

  public static void main(String[] args) {
    final DownloadData downloadData = parseArguments(args);

    downloadData.getInputFile().parallelStream().forEach(songName -> {
      try {
        System.out.println("Fetching \"" + songName + "\"...");
        Metadata metadata = Metadata.build(songName);
        System.out.println("Found Metadata for \"" + songName + "\"...");
        System.out.println("Building MP3 for \"" + songName + "\"...");
        MP3 song = MP3.build(songName, metadata, downloadData.getSaveFolder());
        System.out.println("Created song \"" + song.getFilePath() + "\"");
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    });
  }

  private static void printUsageAndExit() {
    System.out.println("Usage: -f <Path to Input File> -d <Download Folder>");
    System.exit(-1);
  }

  private static DownloadData parseArguments(String[] args) {
    if (args.length != 4) {
      printUsageAndExit();
    }

    String inputFile = null;
    String downloadFolder = null;
    for (int i = 0; i < args.length - 1; i++) {
      switch (args[i].trim()) {
      case "-f":
        inputFile = args[i + 1];
      case "-d":
        downloadFolder = args[i + 1];
      }
    }

    if (inputFile == null || downloadFolder == null) {
      printUsageAndExit();
    }

    List<String> lines = Utils.readFile(inputFile).stream().map(l -> l.trim())
        .filter(l -> !l.isEmpty()).collect(Collectors.toList());

    return new DownloadData(downloadFolder, lines);

  }
}
