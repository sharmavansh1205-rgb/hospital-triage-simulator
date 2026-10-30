package hospital.staff;

/** Abstract parent of all hospital staff. */
public abstract class Staff {

    private final String name;

    protected Staff(String name) {
        this.name = name;
    }

    public abstract String getRole();

    public String getName() { return name; }

    public String describe() {
        return name + " (" + getRole() + ")";
    }
}
