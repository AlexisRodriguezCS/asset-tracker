package com.assettracker.locationservice.service;

/** Thrown when creating a location whose QR tag is already in use. */
public class QrTagTakenException extends RuntimeException {

  public QrTagTakenException(String qrTag) {
    super("QR tag '" + qrTag + "' is already in use");
  }
}
