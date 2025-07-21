package com.photobooth.config;

import java.util.List;

public record TemplateConfig(String name, List<ImagePosition> positions) {
}