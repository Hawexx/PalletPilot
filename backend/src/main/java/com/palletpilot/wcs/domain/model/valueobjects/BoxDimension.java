package com.palletpilot.wcs.domain.model.valueobjects;

public record BoxDimension(int length, int width, int height) {
    public int area() {
        return length * width;
    }

    public int volume() {
        return length * width * height;
    }
}
