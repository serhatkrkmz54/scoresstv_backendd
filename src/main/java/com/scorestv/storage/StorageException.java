package com.scorestv.storage;

/**
 * Nesne depolama (MinIO) işlemleri sırasında oluşan hata.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
