package com.medical.rag.domain.port;

import java.io.InputStream;
import java.util.List;

public interface StoragePort {

    InputStream download(String filePath);

    void upload(String filePath, byte[] content);

    List<String> listFiles(String prefix);
}
