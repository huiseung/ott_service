package com.domain.backend.content.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DataSizeUnit;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;

@ConfigurationProperties(prefix = "app.images")
public class ImageAssetProperties {

    @DataSizeUnit(DataUnit.MEGABYTES)
    private DataSize maxFileSize = DataSize.ofMegabytes(10);

    private double aspectRatioTolerance = 0.03;

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public double getAspectRatioTolerance() {
        return aspectRatioTolerance;
    }

    public void setAspectRatioTolerance(double aspectRatioTolerance) {
        this.aspectRatioTolerance = aspectRatioTolerance;
    }
}
