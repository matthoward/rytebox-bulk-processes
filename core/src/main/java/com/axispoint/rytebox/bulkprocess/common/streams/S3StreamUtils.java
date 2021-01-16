package com.axispoint.rytebox.bulkprocess.common.streams;

import akka.NotUsed;
import akka.japi.Pair;
import akka.stream.alpakka.file.ArchiveMetadata;
import akka.stream.javadsl.Source;
import akka.util.ByteString;

public class S3StreamUtils {
    public Source<ByteString, ?> mergeFileParts(Source<String, NotUsed> headers, String bucketName, String dirPrefix) {
        // TODO: open stream of all files (ordered), and concat:
        //      headers + stream.0 + stream.1 + ... stream.n
        return Source.empty();
    }

    public Source<ByteString, NotUsed> zipFileStreams(Pair<ArchiveMetadata, Source<ByteString, NotUsed>>... streams) {
        // TODO: open stream of all files (ordered), and concat:
        //      headers + stream.0 + stream.1 + ... stream.n
        return Source.empty();
    }
}
