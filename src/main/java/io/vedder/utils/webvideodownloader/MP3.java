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

import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

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

	public static MP3 build(String songName, Metadata metadata,
			String destinationFolder) {

		List<String> lyricVideoIDList = getLyricVideoIdList(songName);

		String tmpPath = System.getProperty("java.io.tmpdir") + "/"
				+ metadata.getBand().replaceAll("/", "") + " - "
				+ metadata.getTitle().replaceAll("/", "") + "_tmp.mp3";
		downloadMp3(lyricVideoIDList, tmpPath, songName);

		String destPath = destinationFolder + "/"
				+ metadata.getTitle().replaceAll("/", "") + ".mp3";
		addMetadata(tmpPath, destPath, metadata);
		return new MP3(destPath, metadata);
	}

	@Override
	public String toString() {
		return "MP3 [filePath=" + filePath + ", metadata=" + metadata + "]";
	}

	private static List<String> getLyricVideoIdList(String songName) {
		Document lyricVideoSearch = Jsoup
				.parse(Utils.sendGet(getVideoLyricSearchUrl(songName)));

		Elements lyricVideoIDElements = lyricVideoSearch.select("div#results")
				.get(0).select("ol > li");

		List<String> lyricVideoIDList = new ArrayList<>();

		for (int i = 2; i < 5; i++) {
			lyricVideoIDList.add(lyricVideoIDElements.get(i).select("div").get(0)
					.attr("data-context-item-id"));
		}

		return lyricVideoIDList;
	}

	private static void addMetadata(String tempPath, String finalPath,
			Metadata metadata) {
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

	private static void downloadMp3(List<String> lyricVideoIDList, String dest,
			String videoName) {

		videoIdLoop: for (String videoId : lyricVideoIDList) {
			URLConnection conn;
			try {

				Utils.sendGet(getMP3SkullUrl(videoId));
				boolean isFinished = false;
				String pollResponseJson = Utils
						.sendGet(getMp3SkullStartPollUrl(videoId));
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
					if (count >= 5) {
						System.out.println("Polling MP3Skullfor video \"" + videoName
								+ "\". Status: " + pollResponse.getJSONObject("progress")
										.getString("progressName"));
						count = 0;
					}
				}

				conn = new URL(getMP3SkullDownloadUrl(videoId)).openConnection();
				conn.setRequestProperty("User-Agent", Utils.getRandomUserAgent());

				InputStream is = conn.getInputStream();

				OutputStream outstream = new FileOutputStream(new File(dest));
				byte[] buffer = new byte[4096];
				int len;
				while ((len = is.read(buffer)) > 0) {
					outstream.write(buffer, 0, len);
				}
				outstream.close();
				return;
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		throw new IllegalStateException(
				"Unable to load any videos for name \"" + videoName + "\"");
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
					+ URLEncoder.encode(songName + " lyrics", "UTF-8");
		} catch (UnsupportedEncodingException e) {
			return null;
		}
	}
}
