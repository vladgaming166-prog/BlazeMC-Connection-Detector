package net.blazemc.connectiondetector.auth;

import java.util.Optional;

public final class MojangLookupResult {

    public enum Status {
        FOUND,
        NOT_FOUND,
        RATE_LIMITED,
        TIMEOUT,
        ERROR,
        INVALID_RESPONSE
    }

    private final Status status;
    private final MojangProfile profile;
    private final String detail;
    private final int httpStatus;

    private MojangLookupResult(Status status, MojangProfile profile, String detail, int httpStatus) {
        this.status = status;
        this.profile = profile;
        this.detail = detail;
        this.httpStatus = httpStatus;
    }

    public static MojangLookupResult found(MojangProfile profile) {
        return new MojangLookupResult(Status.FOUND, profile, "ok", 200);
    }

    public static MojangLookupResult notFound() {
        return new MojangLookupResult(Status.NOT_FOUND, null, "not found", 404);
    }

    public static MojangLookupResult rateLimited(int httpStatus) {
        return new MojangLookupResult(Status.RATE_LIMITED, null, "rate limited", httpStatus);
    }

    public static MojangLookupResult timeout(String detail) {
        return new MojangLookupResult(Status.TIMEOUT, null, detail, -1);
    }

    public static MojangLookupResult error(String detail, int httpStatus) {
        return new MojangLookupResult(Status.ERROR, null, detail, httpStatus);
    }

    public static MojangLookupResult invalidResponse(String detail, int httpStatus) {
        return new MojangLookupResult(Status.INVALID_RESPONSE, null, detail, httpStatus);
    }

    public Status status() {
        return status;
    }

    public Optional<MojangProfile> profile() {
        return Optional.ofNullable(profile);
    }

    public String detail() {
        return detail;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public boolean isUnavailable() {
        return status == Status.RATE_LIMITED
                || status == Status.TIMEOUT
                || status == Status.ERROR
                || status == Status.INVALID_RESPONSE;
    }
}
