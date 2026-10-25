import aquatrack.dao.CustomerDao;
import aquatrack.dao.DeliveryStaffDao;
import aquatrack.dao.LedgerDao;
import aquatrack.dao.OrderDao;
import aquatrack.dao.ProductDao;
import aquatrack.dao.UserDao;
import aquatrack.db.Database;
import aquatrack.model.Customer;
import aquatrack.model.CustomerAccount;
import aquatrack.model.DeliveryStaff;
import aquatrack.model.OrderLine;
import aquatrack.model.Product;
import aquatrack.model.User;
import aquatrack.util.DeliveryRules;
import aquatrack.util.PasswordUtil;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Creates synthetic, practical participant fixtures only in a new empty aquatrack_demo_* database. */
public final class DemoSeed {
    private static final Set<String> TABLES = Set.of("users", "customers", "products", "orders", "order_items",
            "payments", "container_ledger", "delivery_staff");
    private static final String OWNER_PASSWORD = "DemoOwner25!";
    private static final String MANAGER_PASSWORD = "DemoManager25!";
    private static final String STAFF_PASSWORD = "DemoStaff25!";

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].matches("aquatrack_demo_[a-z0-9_]{1,40}")) {
            throw new IllegalArgumentException("Provide a new isolated aquatrack_demo_ database name.");
        }
        String database = args[0];
        String prefix = "jdbc:mysql://127.0.0.1:3306/" + database;
        String url = System.getProperty("aquatrack.db.url");
        if (url == null || !(url.equals(prefix) || url.startsWith(prefix + "?"))) {
            throw new IllegalArgumentException("An explicit JDBC URL for the same isolated local demo database is required.");
        }
        try (Connection guard = Database.getConnection()) {
            require(database.equals(guard.getCatalog()), "Refusing to seed a different catalog.");
            try (Statement query = guard.createStatement(); ResultSet selected = query.executeQuery("SELECT DATABASE()")) {
                require(selected.next() && database.equals(selected.getString(1)), "Refusing to seed a different database.");
            }
            String lockName = database + "_seed";
            boolean locked = false;
            try {
                try (PreparedStatement lock = guard.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                    lock.setString(1, lockName);
                    try (ResultSet result = lock.executeQuery()) {
                        require(result.next() && result.getInt(1) == 1, "Another seed operation is already running.");
                        locked = true;
                    }
                }
                requireEmpty(guard);
                int ownerId = createInitialOwner(guard);
                UserDao accounts = new UserDao();
                User manager = accounts.save(ownerId, -1, "demo_manager", "Demo Manager", "Owner/Admin", true,
                        PasswordUtil.hash(MANAGER_PASSWORD));
                User cashier = accounts.save(ownerId, -1, "demo_staff", "Demo Staff", "Staff", true,
                        PasswordUtil.hash(STAFF_PASSWORD));
                require(manager.isOwner() && cashier.isActive(), "Demo accounts could not be created.");
                seedStation(ownerId, cashier.getId());
                verifyPasswords(accounts);
                System.out.println("Synthetic participant fixtures are ready in " + database + ".");
                System.out.println("Demo-only sign-ins (case sensitive passwords):");
                System.out.println("  demo_owner   / " + OWNER_PASSWORD + "   (Owner/Admin)");
                System.out.println("  demo_manager / " + MANAGER_PASSWORD + " (Owner/Admin)");
                System.out.println("  demo_staff   / " + STAFF_PASSWORD + "   (Staff)");
                System.out.println("For the full evaluation use demo_owner; Accounts and price editing require an owner.");
            } finally {
                if (locked) try (PreparedStatement release = guard.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                    release.setString(1, lockName);
                    release.execute();
                }
            }
        }
    }

    private static void requireEmpty(Connection connection) throws Exception {
        Set<String> actual = new HashSet<>();
        try (Statement query = connection.createStatement(); ResultSet tables = query.executeQuery(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'")) {
            while (tables.next()) actual.add(tables.getString(1));
        }
        require(actual.equals(TABLES), "Demo database must contain exactly the eight baseline tables.");
        for (String table : TABLES) {
            try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery("SELECT COUNT(*) FROM " + table)) {
                require(rows.next() && rows.getInt(1) == 0,
                        "Refusing to overwrite nonempty table " + table + ". Create a new demo database.");
            }
        }
        try (Statement query = connection.createStatement(); ResultSet column = query.executeQuery(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'products' AND COLUMN_NAME = 'delivery_price'")) {
            require(column.next() && column.getInt(1) == 1, "Apply the delivery-price migration before seeding.");
        }
    }

    private static int createInitialOwner(Connection connection) throws Exception {
        // An empty database needs its first owner before the actor-authorized account DAO can run.
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO users "
                + "(username, password_hash, full_name, role, is_active) VALUES (?, ?, ?, 'Owner/Admin', 1)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, "demo_owner");
            insert.setString(2, PasswordUtil.hash(OWNER_PASSWORD));
            insert.setString(3, "Demo Owner");
            insert.executeUpdate();
            try (ResultSet key = insert.getGeneratedKeys()) {
                require(key.next(), "The initial owner ID was not returned.");
                return key.getInt(1);
            }
        }
    }

    private static void seedStation(int ownerId, int cashierId) throws Exception {
        ProductDao products = new ProductDao();
        products.insert("5-gallon purified refill", Product.REFILL, money("25"), money("30"), 0);
        products.insert("3-gallon purified refill", Product.REFILL, money("15"), money("20"), 0);
        products.insert("New 5-gallon container", Product.CONTAINER, money("180"), null, 3);
        products.insert("Container cap", Product.CAP_SEAL, money("5"), null, 10);
        products.insert("Container seal", Product.CAP_SEAL, money("3"), null, 10);
        products.insert("Archived refill promotion", Product.REFILL, money("20"), money("25"), 0);
        Product refill5 = products.findByName("5-gallon purified refill", 0);
        Product refill3 = products.findByName("3-gallon purified refill", 0);
        Product container = products.findByName("New 5-gallon container", 0);
        Product cap = products.findByName("Container cap", 0);
        products.restock(container.getId(), 12);
        products.restock(cap.getId(), 4);
        products.setActive(products.findByName("Archived refill promotion", 0).getId(), false);

        CustomerDao customers = new CustomerDao();
        Customer ana = customer(customers, "Demo - Ana Reyes", "Demo address: Poblacion, Tagbilaran City, Bohol", "09170000101");
        Customer ben = customer(customers, "Demo - Ben Santos", "Demo address: Cogon, Tagbilaran City, Bohol", "09170000102");
        Customer carla = customer(customers, "Demo - Carla Cruz", "Demo address: Dao, Tagbilaran City, Bohol", "09170000103");
        Customer dario = customer(customers, "Demo - Dario Lim", "Demo address: Dampas, Tagbilaran City, Bohol", "09170000104");

        DeliveryStaffDao riders = new DeliveryStaffDao();
        riders.insert("Demo Rider Alex", "09170000201");
        riders.insert("Demo Rider Sam", "09170000202");
        DeliveryStaff rider = riders.findAll(true).stream().filter(person -> person.getFullName().equals("Demo Rider Alex"))
                .findFirst().orElseThrow();

        OrderDao orders = new OrderDao();
        LedgerDao ledger = new LedgerDao();
        orders.placeOrder(ana.getId(), "Walk-in", "Credit", cashierId, 4, 0,
                List.of(line(refill5, 4, "Walk-in")));
        ledger.recordPayment(ana.getId(), money("25"));
        int benOrder = orders.placeOrder(ben.getId(), "Delivery", "Credit", ownerId, 2, 0,
                List.of(line(refill5, 2, "Delivery"), line(cap, 1, "Delivery")));
        require(orders.assignRider(benOrder, rider.getId()) == 1, "Could not assign the demo delivery rider.");
        require(orders.setStatus(benOrder, DeliveryRules.PENDING, DeliveryRules.OUT) == 1,
                "Could not set the demo delivery status.");
        orders.placeOrder(carla.getId(), "Walk-in", "Cash", cashierId, 0, 0,
                List.of(line(container, 1, "Walk-in"), line(refill3, 1, "Walk-in")));
        orders.placeOrder(dario.getId(), "Delivery", "Cash", ownerId, 2, 0,
                List.of(line(refill3, 2, "Delivery")));

        CustomerAccount anaBalance = ledger.account(ana.getId());
        CustomerAccount benBalance = ledger.account(ben.getId());
        require(anaBalance.getUnpaid().compareTo(money("75")) == 0 && anaBalance.getGallonsOut() == 4,
                "Ana's payment practice balance must be PHP 75 with four containers.");
        require(benBalance.getUnpaid().compareTo(money("65")) == 0 && benBalance.getGallonsOut() == 2,
                "Ben's payment practice balance must be PHP 65 with two containers.");
        require("In Stock".equals(products.findByName("New 5-gallon container", 0).getAvailability())
                && "Low Stock".equals(products.findByName("Container cap", 0).getAvailability())
                && "Out of Stock".equals(products.findByName("Container seal", 0).getAvailability()),
                "Demo inventory must show normal, low and zero stock examples.");
        System.out.println("Prices: 5-gallon pickup/delivery PHP 25/30; 3-gallon PHP 15/20; container PHP 180; cap PHP 5; seal PHP 3.");
        System.out.println("Inventory: 11 containers (In Stock), 3 caps (Low Stock), 0 seals (Out of Stock), plus an archived refill.");
        System.out.println("Practice balances: Demo - Ana Reyes owes PHP 75 with 4 containers; Demo - Ben Santos owes PHP 65 with 2 containers.");
        System.out.println("Four sample orders include paid pickup, credit, Pending delivery and Out for Delivery; two demo riders are available.");
    }

    private static Customer customer(CustomerDao customers, String name, String address, String contact) throws Exception {
        customers.insert(name, address, contact);
        Customer created = customers.findByName(name, 0);
        require(created != null, "Demo customer was not returned.");
        return created;
    }
    private static OrderLine line(Product product, int quantity, String orderType) {
        return OrderLine.priced(product.getId(), product.getName(), quantity, product.priceForOrderType(orderType));
    }
    private static void verifyPasswords(UserDao accounts) throws Exception {
        require(PasswordUtil.verify(OWNER_PASSWORD, accounts.findByUsername("demo_owner").getPasswordHash())
                && PasswordUtil.verify(MANAGER_PASSWORD, accounts.findByUsername("demo_manager").getPasswordHash())
                && PasswordUtil.verify(STAFF_PASSWORD, accounts.findByUsername("demo_staff").getPasswordHash()),
                "Demo password hashes did not verify.");
    }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
