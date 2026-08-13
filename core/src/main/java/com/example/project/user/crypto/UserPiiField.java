package com.example.project.user.crypto;

public enum UserPiiField {
    EMAIL("morizoom:user:email"),
    PHONE("morizoom:user:phone"),
    USER_NAME("morizoom:user:name"),
    FAMILY_NAME("morizoom:family:name"),
    FAMILY_BIRTH_DATE("morizoom:family:birth-date");

    private final String associatedData;

    UserPiiField(String associatedData) {
        this.associatedData = associatedData;
    }

    public String associatedData() {
        return associatedData;
    }
}
