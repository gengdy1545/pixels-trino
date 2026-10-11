/*
 * Copyright 2026 PixelsDB.
 *
 * This file is part of Pixels.
 *
 * Pixels is free software: you can redistribute it and/or modify
 * it under the terms of the Affero GNU General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 */
package io.pixelsdb.pixels.trino.split;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import static java.util.Objects.requireNonNull;

/**
 * Metadata-owned description used only when a virtual ingest file has already
 * left the Retina hot view.
 */
public final class PixelsFileDescriptor
{
    private final long fileId;
    private final String path;
    private final int rowGroupCount;
    private final String storageScheme;

    @JsonCreator
    public PixelsFileDescriptor(
            @JsonProperty("fileId") long fileId,
            @JsonProperty("path") String path,
            @JsonProperty("rowGroupCount") int rowGroupCount,
            @JsonProperty("storageScheme") String storageScheme)
    {
        this.fileId = fileId;
        this.path = requireNonNull(path, "path is null");
        this.rowGroupCount = rowGroupCount;
        this.storageScheme = requireNonNull(storageScheme, "storageScheme is null");
    }

    @JsonProperty
    public long getFileId()
    {
        return fileId;
    }

    @JsonProperty
    public String getPath()
    {
        return path;
    }

    @JsonProperty
    public int getRowGroupCount()
    {
        return rowGroupCount;
    }

    @JsonProperty
    public String getStorageScheme()
    {
        return storageScheme;
    }
}
