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
package io.pixelsdb.pixels.trino;

import io.pixelsdb.pixels.retina.RetinaProto;
import io.pixelsdb.pixels.trino.split.PixelsFileDescriptor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reconciles the metadata view captured during split planning with a later
 * write-buffer snapshot. Regular files keep their existing file splits;
 * missing virtual files become fallback file reads.
 */
final class WriteBufferSourceSelector
{
    private WriteBufferSourceSelector() {}

    static Selection select(
            List<Long> regularFileIds,
            List<PixelsFileDescriptor> virtualFiles,
            RetinaProto.GetWriteBufferResponse response)
    {
        List<Long> blockIds = response.getBlockIdsList();
        List<Long> sourceFileIds = response.getFileIdsList();
        List<RetinaProto.VisibilityBitmap> bitmaps = response.getBitmapsList();
        int expectedSourceCount = blockIds.size() + 1;
        if (sourceFileIds.size() != expectedSourceCount || bitmaps.size() != expectedSourceCount)
        {
            throw new IllegalArgumentException(String.format(
                    "Invalid write-buffer response: blockIds=%d, fileIds=%d, bitmaps=%d",
                    blockIds.size(), sourceFileIds.size(), bitmaps.size()));
        }

        Set<Long> regularIds = new HashSet<>(regularFileIds);
        Set<Long> representedFileIds = new HashSet<>();
        representedFileIds.add(sourceFileIds.get(0));
        byte[] activeData = response.getData().toByteArray();

        List<Long> hotObjectIds = new ArrayList<>();
        List<RetinaProto.VisibilityBitmap> hotBitmaps = new ArrayList<>();
        hotBitmaps.add(bitmaps.get(0));
        for (int i = 0; i < blockIds.size(); i++)
        {
            long fileId = sourceFileIds.get(i + 1);
            representedFileIds.add(fileId);
            if (!regularIds.contains(fileId))
            {
                hotObjectIds.add(blockIds.get(i));
                hotBitmaps.add(bitmaps.get(i + 1));
            }
        }

        List<PixelsFileDescriptor> fallbackFiles = virtualFiles.stream()
                .filter(file -> !representedFileIds.contains(file.getFileId()))
                .toList();
        return new Selection(activeData, hotObjectIds, hotBitmaps, fallbackFiles);
    }

    static final class Selection
    {
        private final byte[] activeData;
        private final List<Long> hotObjectIds;
        private final List<RetinaProto.VisibilityBitmap> hotBitmaps;
        private final List<PixelsFileDescriptor> fallbackFiles;

        private Selection(
                byte[] activeData,
                List<Long> hotObjectIds,
                List<RetinaProto.VisibilityBitmap> hotBitmaps,
                List<PixelsFileDescriptor> fallbackFiles)
        {
            this.activeData = activeData;
            this.hotObjectIds = hotObjectIds;
            this.hotBitmaps = hotBitmaps;
            this.fallbackFiles = fallbackFiles;
        }

        byte[] getActiveData()
        {
            return activeData;
        }

        List<Long> getHotObjectIds()
        {
            return hotObjectIds;
        }

        List<RetinaProto.VisibilityBitmap> getHotBitmaps()
        {
            return hotBitmaps;
        }

        List<PixelsFileDescriptor> getFallbackFiles()
        {
            return fallbackFiles;
        }
    }
}
