package dev.bravozulu.sitrep.auth.internal.credential;

import dev.bravozulu.sitrep.shared.exceptions.BadRequestException;

public class InvalidActivationTokenException extends BadRequestException {
  public InvalidActivationTokenException() {
    super(
        "This activation link is invalid or has already been used. If you have already set your"
            + " password, log in; otherwise ask your administrator for a new link.");
  }
}
