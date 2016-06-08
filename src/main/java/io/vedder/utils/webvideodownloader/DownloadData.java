package io.vedder.utils.webvideodownloader;

import java.util.List;

public class DownloadData {
  private final String saveFolder;
  private final List<String> inputFile;

  public DownloadData(String saveFolder, List<String> inputFile) {
    super();
    this.saveFolder = saveFolder;
    this.inputFile = inputFile;
  }

  public String getSaveFolder() {
    return saveFolder;
  }

  public List<String> getInputFile() {
    return inputFile;
  }

}
