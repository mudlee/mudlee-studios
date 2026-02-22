package hu.mudlee.core.settings;

public class WindowPreferences {
  private final Antialiasing antialiasing;
  private final boolean fullscreen;
  private final String title;
  private final int width;
  private final int height;
  private final boolean vSync;

  private WindowPreferences(Builder builder) {
    this.antialiasing = builder.antialiasing;
    this.fullscreen = builder.fullscreen;
    this.title = builder.title;
    this.width = builder.width;
    this.height = builder.height;
    this.vSync = builder.vSync;
  }

  public Antialiasing getAntialiasing() {
    return antialiasing == null ? Antialiasing.OFF : antialiasing;
  }

  public boolean isFullscreen() {
    return fullscreen;
  }

  public String getTitle() {
    return title == null ? "Sandbox" : title;
  }

  public int getWidth() {
    return width == 0 ? 1024 : width;
  }

  public int getHeight() {
    return height == 0 ? 768 : height;
  }

  public boolean isvSync() {
    return vSync;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Antialiasing antialiasing;
    private boolean fullscreen;
    private String title;
    private int width;
    private int height;
    private boolean vSync;

    public Builder title(String title) {
      this.title = title;
      return this;
    }

    public Builder fullscreen(boolean fullscreen) {
      this.fullscreen = fullscreen;
      return this;
    }

    public Builder antialiasing(Antialiasing antialiasing) {
      this.antialiasing = antialiasing;
      return this;
    }

    public Builder width(int width) {
      this.width = width;
      return this;
    }

    public Builder height(int height) {
      this.height = height;
      return this;
    }

    public Builder vSync(boolean vSync) {
      this.vSync = vSync;
      return this;
    }

    public WindowPreferences build() {
      return new WindowPreferences(this);
    }
  }
}
