package io.vedder.utils.webvideodownloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.ID3v24Tag;
import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.NotSupportedException;
import com.mpatric.mp3agic.UnsupportedTagException;

public class MP3 {

  private final String filePath;
  private final Metadata metadata;

  public MP3(String filePath, Metadata metadata) {
    this.filePath = filePath;
    this.metadata = metadata;
  }

  public String getFilePath() {
    return filePath;
  }

  public Metadata getMetadata() {
    return metadata;
  }

  public static MP3 build(String songName, Metadata metadata, String destinationFolder) {

    List<String> lyricVideoIDList = getLyricVideoIdList(songName);

    String tmpPath =
        System.getProperty("java.io.tmpdir") + "/" + metadata.getBand().replaceAll("/", "") + " - "
            + metadata.getTitle().replaceAll("/", "") + "_tmp.mp3";
    downloadMp3(lyricVideoIDList, tmpPath, songName);

    String destPath = destinationFolder + "/" + metadata.getTitle().replaceAll("/", "") + ".mp3";
    addMetadata(tmpPath, destPath, metadata);
    return new MP3(destPath, metadata);
  }

  @Override
  public String toString() {
    return "MP3 [filePath=" + filePath + ", metadata=" + metadata + "]";
  }

  private static List<String> getLyricVideoIdList(String songName) {
    String searchUrl = getVideoLyricSearchUrl(songName);
    System.out.println("MP3 Search URL: " + searchUrl);
    Document lyricVideoSearch = Jsoup.parse(Utils.sendGet(searchUrl));


    lyricVideoSearch.select("div#results").get(0)
        .select("div.yt-lockup-dismissable > div.yt-lockup-content > h3.yt-lockup-title").get(0)
        .select("a[href]").get(0);

    List<Element> idElements = new ArrayList<>(5);

    for (int i = 0; i < 5; i++) {
      idElements.add(lyricVideoSearch.select("div#results").get(0)
          .select("div.yt-lockup-dismissable > div.yt-lockup-content > h3.yt-lockup-title").get(i)
          .select("a[href]").get(0));
    }

    List<String> ids = idElements.stream().map(e -> e.attr("href").replace("/watch?v=", ""))
        .collect(Collectors.toList());
    return ids;

  }

  private static void addMetadata(String tempPath, String finalPath, Metadata metadata) {
    Mp3File mp3file;
    try {
      mp3file = new Mp3File(tempPath);

      ID3v2 id3v2Tag;
      if (mp3file.hasId3v2Tag()) {
        id3v2Tag = mp3file.getId3v2Tag();
      } else {
        id3v2Tag = new ID3v24Tag();
        mp3file.setId3v2Tag(id3v2Tag);
      }
      id3v2Tag.setArtist(metadata.getBand());
      id3v2Tag.setTitle(metadata.getTitle());
      id3v2Tag.setAlbumImage(metadata.getImageData(), "image/jpeg");
      mp3file.save(finalPath);
    } catch (UnsupportedTagException e) {
      e.printStackTrace();
    } catch (InvalidDataException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (NotSupportedException e) {
      e.printStackTrace();
    }
  }

  private static void downloadMp3(List<String> lyricVideoIDList, String dest, String videoName) {

    videoIdLoop: for (String videoId : lyricVideoIDList) {
      if (videoId.isEmpty()) {
        System.out.println("Empty video ID..");
        continue videoIdLoop;
      } else {
        System.out.println(videoId);
      }
      Utils.sendGet(getMP3SkullUrl(videoId));
      boolean isFinished = false;
      String pollResponseJson = Utils.sendGet(getMp3SkullStartPollUrl(videoId));
      for (int count = 0; !isFinished; count++) {
        JSONObject pollResponse = new JSONObject(pollResponseJson);
        if (pollResponse.getJSONObject("progress").getString("progressName")
            .equals("Not allowed in your country.")) {
          System.out.println("Video for song \"" + videoName
              + "\" not allowed in this country, trying new video...");
          continue videoIdLoop; // Try Next Video Id
        }
        isFinished = pollResponse.getBoolean("finished");
        if (!isFinished) {
          pollResponseJson = Utils.sendGet(getMp3SkullPollUrl(videoId));
        }
        System.out.println("Polling MP3Skull for video \"" + videoName + "\". Status: "
            + pollResponse.getJSONObject("progress").getString("progressName"));
        if (count >= 15) {
          count = 0;
          System.out.println("Trying video download");
          if (tryVideoDownload(videoId, dest)) {
            System.out.println("Video download success!");
            return;
          } else {
            System.out.println("Video download failed...");
          }
        }
      }
      System.out.println("Downloading \"" + videoName + "\"");
      tryVideoDownload(videoId, dest);
      return;
    }
    throw new IllegalStateException("Unable to load any videos for name \"" + videoName + "\"");
  }

  private static boolean tryVideoDownload(String videoId, String dest) {
    URLConnection conn;
    try {
      String mp3SkullDownloadUrl = getMP3SkullDownloadUrl(videoId);
      conn = new URL(mp3SkullDownloadUrl).openConnection();
      conn.setRequestProperty("User-Agent", Utils.getRandomUserAgent());

      InputStream is = conn.getInputStream();

      OutputStream outstream = new FileOutputStream(new File(dest));
      byte[] buffer = new byte[4096];
      int len;
      while ((len = is.read(buffer)) > 0) {
        outstream.write(buffer, 0, len);
      }
      outstream.close();
      return true;
    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    return false;
  }

  private static String getMP3SkullUrl(String videoId) {
    return "https://mp3skull.onl/api/youtube/frame/#/?id=" + videoId;
  }

  private static String getMP3SkullDownloadUrl(String videoId) {
    return "http://serve01.mp3skull.onl/get?id=" + videoId;
  }

  private static String getMp3SkullStartPollUrl(String videoId) {
    return "https://mp3skull.onl/api/youtube/state?id=" + videoId;
  }

  private static String getMp3SkullPollUrl(String videoId) {
    return "https://mp3skull.onl/api/youtube/state?id=" + videoId + "&t="
        + String.valueOf(System.currentTimeMillis());
  }

  private static String getVideoLyricSearchUrl(String songName) {
    try {
      return "https://www.youtube.com/results?search_query="
          + URLEncoder.encode(songName + " lyrics -vevo -chipmunk", "UTF-8");
    } catch (UnsupportedEncodingException e) {
      return null;
    }
  }
}
