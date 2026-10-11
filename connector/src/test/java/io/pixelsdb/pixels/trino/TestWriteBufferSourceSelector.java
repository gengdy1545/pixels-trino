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

import com.google.protobuf.ByteString;
import io.pixelsdb.pixels.retina.RetinaProto;
import io.pixelsdb.pixels.trino.split.PixelsFileDescriptor;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestWriteBufferSourceSelector
{
    @Test
    public void reconcilesRegularVirtualAndRetinaSetsByLogicalFile()
    {
        List<Long> regular = List.of(1L);
        List<PixelsFileDescriptor> virtual = List.of(file(2L), file(3L));
        RetinaProto.GetWriteBufferResponse response = response(
                ByteString.EMPTY, 99L,
                List.of(10L, 11L, 20L, 40L),
                List.of(1L, 1L, 2L, 4L));

        WriteBufferSourceSelector.Selection selection =
                WriteBufferSourceSelector.select(regular, virtual, response);

        assertEquals(List.of(20L, 40L), selection.getHotObjectIds());
        assertEquals(3, selection.getHotBitmaps().size());
        assertEquals(2L, selection.getHotBitmaps().get(1).getBitmap(0));
        assertEquals(3L, selection.getHotBitmaps().get(2).getBitmap(0));
        assertEquals(List.of(3L), selection.getFallbackFiles().stream()
                .map(PixelsFileDescriptor::getFileId).toList());
    }

    @Test
    public void oneResidualSegmentKeepsWholeVirtualFileOutOfFallback()
    {
        WriteBufferSourceSelector.Selection selection = WriteBufferSourceSelector.select(
                List.of(), List.of(file(7L)),
                response(ByteString.EMPTY, 99L, List.of(71L), List.of(7L)));

        assertEquals(List.of(71L), selection.getHotObjectIds());
        assertEquals(0, selection.getFallbackFiles().size());
    }

    @Test
    public void emptySuccessfulSnapshotStillOccupiesActiveFileId()
    {
        WriteBufferSourceSelector.Selection selection = WriteBufferSourceSelector.select(
                List.of(), List.of(file(8L), file(9L)),
                response(ByteString.EMPTY, 8L, List.of(), List.of()));

        assertEquals(0, selection.getActiveData().length);
        assertEquals(List.of(9L), selection.getFallbackFiles().stream()
                .map(PixelsFileDescriptor::getFileId).toList());
    }

    @Test
    public void activeDataRepresentsItsLogicalFile()
    {
        WriteBufferSourceSelector.Selection selection = WriteBufferSourceSelector.select(
                List.of(), List.of(file(8L)),
                response(ByteString.copyFromUtf8("active"), 8L, List.of(), List.of()));

        assertEquals(0, selection.getFallbackFiles().size());
        assertEquals("active", new String(selection.getActiveData(), StandardCharsets.UTF_8));
    }

    @Test
    public void rejectsMisalignedResponse()
    {
        RetinaProto.GetWriteBufferResponse response = RetinaProto.GetWriteBufferResponse.newBuilder()
                .addFileIds(1L)
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> WriteBufferSourceSelector.select(List.of(), List.of(), response));
    }

    private static PixelsFileDescriptor file(long fileId)
    {
        return new PixelsFileDescriptor(fileId, "s3://bucket/" + fileId + ".pxl", 1, "s3");
    }

    private static RetinaProto.GetWriteBufferResponse response(
            ByteString activeData,
            long activeFileId,
            List<Long> blockIds,
            List<Long> objectFileIds)
    {
        RetinaProto.GetWriteBufferResponse.Builder builder =
                RetinaProto.GetWriteBufferResponse.newBuilder()
                        .setData(activeData)
                        .addFileIds(activeFileId)
                        .addBitmaps(RetinaProto.VisibilityBitmap.getDefaultInstance())
                        .addAllBlockIds(blockIds)
                        .addAllFileIds(objectFileIds);
        for (int i = 0; i < blockIds.size(); i++)
        {
            builder.addBitmaps(RetinaProto.VisibilityBitmap.newBuilder().addBitmap(i));
        }
        return builder.build();
    }
}
