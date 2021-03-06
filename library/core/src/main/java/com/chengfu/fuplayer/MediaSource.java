package com.chengfu.fuplayer;

import android.net.Uri;

import com.chengfu.fuplayer.player.IPlayer;

import java.util.Map;

/**
 * Defines and provides media to be played by an {@link IPlayer}.
 */
public class MediaSource {
    private String path;
    private Uri uri;
    private Map<String, String> headers;

    public MediaSource() {

    }

    public MediaSource(Builder builder) {
        this.path = builder.path;
        this.uri = builder.uri;
        this.headers = builder.headers;
    }

    public MediaSource(String path) {
        this.path = path;
    }

    public MediaSource(Uri uri) {
        this.uri = uri;
    }

    public MediaSource(Uri uri, Map<String, String> headers) {
        this.uri = uri;
        this.headers = headers;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public Uri getUri() {
        return uri;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    @Override
    public String toString() {
        return this.path;
    }

    public static class Builder {
        private String path;
        private Uri uri;
        private Map<String, String> headers;

        public Builder() {

        }

        public Builder(String path) {
            this.path = path;
        }

        public Builder(Uri uri) {
            this.uri = uri;
        }

        public Builder(Uri uri, Map<String, String> headers) {
            this.uri = uri;
            this.headers = headers;
        }

        public Builder setPath(String path) {
            return new Builder(path);
        }

        public Builder setUri(Uri uri) {
            return new Builder(uri);
        }

        public Builder setHeaders(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public MediaSource build() {
            return new MediaSource(this);
        }

    }

}
