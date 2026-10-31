package io.takamiya.mio.bean;

public class Developer {

    private String name;
    private String role;
    private String description;
    private String avatarUrl;

    public Developer() {
    }

    public Developer(String name, String role, String description, String avatarUrl) {
        this.name = name;
        this.role = role;
        this.description = description;
        this.avatarUrl = avatarUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
}
