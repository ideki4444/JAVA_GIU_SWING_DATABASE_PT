package aquatrack.model;

/** A login account. The password hash is kept only so the sign-in screen can check it. */
public final class User {

    private final int id;
    private final String username;
    private final String fullName;
    private final String role;
    private final String passwordHash;
    private final boolean active;

    public User(int id, String username, String fullName, String role, String passwordHash) {
        this(id, username, fullName, role, passwordHash, true);
    }

    public User(int id, String username, String fullName, String role, String passwordHash, boolean active) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.passwordHash = passwordHash;
        this.active = active;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
    public String getPasswordHash() { return passwordHash; }
    public boolean isActive() { return active; }

    public boolean isOwner() {
        return "Owner/Admin".equals(role);
    }
}
