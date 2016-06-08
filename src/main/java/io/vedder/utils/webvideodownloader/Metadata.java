package io.vedder.utils.webvideodownloader;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;

import javax.imageio.ImageIO;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class Metadata {
  private final String band;
  private final String title;
  private final byte[] imageData;

  public static Metadata build(String songName) {
    String band = null;
    String title = null;
    byte[] imageData = null;

    boolean isValidTitle = false;

    isValidTitleLoop: while (!isValidTitle) {
      Document thumbnailVideoSearch = Jsoup
          .parse(Utils.sendGet(getImageSearchUrl(songName)));

      String thumbnailVideoID = thumbnailVideoSearch.select("div#results")
          .get(0).select("ol > li").get(2).select("div").get(0)
          .attr("data-context-item-id");

      Document thumbnailVideo = Jsoup
          .parse(Utils.sendGet(getVideoUrl(thumbnailVideoID)));

      String videoTitle = thumbnailVideo.select("title").get(0).text()
          .replace(" - YouTube", "");

      if (videoTitle.equals("YouTube") || videoTitle.equals(" - YouTube")) {
        System.out.println(
            "Invalid title for song \"" + songName + "\", retrying...");
        continue isValidTitleLoop; // Retry
      }

      String[] splitVideoTitle = videoTitle.split("-");

      if (splitVideoTitle.length < 2) {
        continue isValidTitleLoop; // Retry
      }

      band = splitVideoTitle[0].trim();
      title = splitVideoTitle[1].replaceAll("\\(([^)]+)\\)", "")
          .replaceAll("\\[([^)]+)\\]", "").trim();

      if (band != null && title != null) {
        isValidTitle = true;
      } else {
        continue isValidTitleLoop; // Retry
      }

      try {
        URL albumArtUrl = new URL(getImageUrl(thumbnailVideoID));
        BufferedImage image = ImageIO.read(albumArtUrl);

        File imgPath = new File("/tmp/" + title.replace("/", "") + ".jpg");

        ImageIO.write(image, "jpg", imgPath);
        RandomAccessFile file = new RandomAccessFile(imgPath, "r");
        imageData = new byte[(int) file.length()];
        file.read(imageData);
        file.close();

      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    return new Metadata(band, title, imageData);
  }

  private static String getVideoUrl(String videoId) {
    return "https://www.youtube.com/watch?v=" + videoId;
  }

  private static String getImageSearchUrl(String songName) {
    try {
      return "https://www.youtube.com/results?search_query="
          + URLEncoder.encode(songName, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      return null;
    }
  }

  private static String getImageUrl(String videoId) {
    return "http://img.youtube.com/vi/" + videoId + "/0.jpg";
  }

  private Metadata(String band, String title, byte[] imageData) {
    this.band = band;
    this.title = title;
    this.imageData = imageData;
  }

  @Override
  public String toString() {
    return "Metadata [band=" + band + ", title=" + title + "]";
  }

  public String getBand() {
    return band;
  }

  public String getTitle() {
    return title;
  }

  public byte[] getImageData() {
    return imageData;
  }
}