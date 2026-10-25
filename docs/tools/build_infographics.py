#!/usr/bin/env python3
"""Rebuild AquaTrack's editable SVG infographics with Python's standard library.

Run from any directory: python3 docs/tools/build_infographics.py
All shapes and text are vector content; no downloaded fonts or assets are used.
"""

from html import escape
from pathlib import Path

OUT = Path(__file__).resolve().parents[1] / "images"
NAVY = "#11283F"
TEAL = "#087F82"
GOLD = "#B67913"
INK = "#203B50"
MUTED = "#4A6173"
PALE = "#E8F5F3"
AMBER = "#FFF2D8"
BG = "#F5F8FB"
WHITE = "#FFFFFF"
LINE = "#CEDBE4"


class Plate:
    def __init__(self, filename, number, title, subtitle, description, height=1250):
        self.filename = filename
        self.height = height
        self.parts = [
            f'<svg xmlns="http://www.w3.org/2000/svg" width="1100" height="{height}" viewBox="0 0 1100 {height}" role="img" aria-labelledby="title desc">',
            f'<title id="title">{escape(title)}</title>',
            f'<desc id="desc">{escape(description)}</desc>',
            '<defs><marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="5" orient="auto" markerUnits="userSpaceOnUse"><path d="M 0 0 L 9 5 L 0 10 z" fill="#087F82"/></marker></defs>',
            f'<rect width="1100" height="{height}" fill="{BG}"/>',
            f'<rect width="1100" height="145" fill="{NAVY}"/>',
        ]
        self.text(48, 37, f"AQUATRACK  /  FIELD GUIDE {number}", 20, "#91DBD5", 700)
        self.text(48, 84, title, 36, WHITE, 700)
        self.text(48, 120, subtitle, 24, "#D3E2EC")

    def text(self, x, y, value, size=24, color=INK, weight=400, anchor="start"):
        self.parts.append(f'<text x="{x}" y="{y}" fill="{color}" font-family="DejaVu Sans, Arial, sans-serif" font-size="{size}" font-weight="{weight}" text-anchor="{anchor}">{escape(value)}</text>')

    def lines(self, x, y, values, size=24, color=INK, spacing=34, weight=400):
        for i, value in enumerate(values):
            self.text(x, y + i * spacing, value, size, color, weight)

    def rect(self, x, y, width, height, fill=WHITE, stroke=LINE, radius=18):
        self.parts.append(f'<rect x="{x}" y="{y}" width="{width}" height="{height}" rx="{radius}" fill="{fill}" stroke="{stroke}" stroke-width="1.5"/>')

    def arrow(self, x1, y1, x2, y2, dashed=False):
        dash = ' stroke-dasharray="8 6"' if dashed else ""
        self.parts.append(f'<path d="M {x1} {y1} L {x2} {y2}" fill="none" stroke="{TEAL}" stroke-width="3" marker-end="url(#arrow)"{dash}/>')

    def card(self, x, y, width, height, heading, body, tag=None, fill=WHITE):
        self.rect(x, y, width, height, fill)
        left = x + 24
        if tag is not None:
            self.rect(left, y + 19, 43, 40, TEAL, TEAL, 10)
            self.text(left + 21.5, y + 47, tag, 24, WHITE, 700, "middle")
            self.text(left + 58, y + 48, heading, 27, NAVY, 700)
        else:
            self.text(left, y + 44, heading, 27, NAVY, 700)
        self.lines(left, y + 85, body, spacing=31)

    def note(self, y, title, lines, fill=PALE, height=130):
        self.rect(48, y, 1004, height, fill, fill)
        self.text(72, y + 37, title, 26, TEAL if fill == PALE else GOLD, 700)
        self.lines(72, y + 74, lines, size=24, spacing=33)

    def save(self):
        self.parts.append(f'<path d="M 48 {self.height - 52} H 1052" stroke="{LINE}"/>')
        self.text(48, self.height - 24, "AquaTrack v3  •  Read the README for exact commands and screen instructions", 19, MUTED)
        self.parts.append("</svg>")
        OUT.mkdir(parents=True, exist_ok=True)
        path = OUT / self.filename
        path.write_text("\n".join(self.parts) + "\n", encoding="utf-8")
        print(path.relative_to(OUT.parent.parent))


def operating():
    p = Plate("01-operating-workflow.svg", "01", "From installation to a working day", "Follow the numbered path; the database holds the live business records.",
              "Eight steps: install Java and MySQL; configure and compile; import schema and bootstrap first owner; prepare products, stock, customers and accounts; create a sale; print and deliver; collect payments and containers; review reports and back up the database.", 1310)
    cards = [
        (48, 175, "Prepare the computer", ["JDK 17+ and a desktop session.", "Start MySQL / MariaDB.", "Get Aquatrack_version-3."], "1"),
        (578, 175, "Configure and compile", ["Edit Database.java settings.", "Build the Java source.", "Keep the JDBC JAR available."], "2"),
        (578, 405, "Create the first login", ["Import sql/schema.sql.", "Hash your chosen password.", "Run bootstrap-owner.sql.", "There is no default app login."], "3"),
        (48, 405, "Prepare business data", ["Owner: add products and prices.", "Receive opening stock.", "Add customers, riders, logins."], "4"),
        (48, 635, "Build and save a sale", ["Choose customer and products.", "Set Walk-in / Delivery and", "Cash / Credit; enter gallons.", "Review, then save the order."], "5"),
        (578, 635, "Print and dispatch", ["The sale saves before printing.", "For delivery: assign rider,", "Out for delivery, then", "Mark delivered."], "6"),
        (578, 865, "Collect and return", ["Containers & Balances:", "record credit payments and", "gallons borrowed or returned.", "Check each updated balance."], "7"),
        (48, 865, "Review and preserve", ["Owner: review Reports.", "Compare sales, debts and stock.", "Back up MySQL separately", "from the Java source files."], "8"),
    ]
    for x, y, title, body, tag in cards:
        p.card(x, y, 474, 210, title, body, tag)
    p.arrow(526, 280, 574, 280)
    p.arrow(815, 389, 815, 401)
    p.arrow(574, 510, 526, 510)
    p.arrow(285, 619, 285, 631)
    p.arrow(526, 740, 574, 740)
    p.arrow(815, 849, 815, 861)
    p.arrow(574, 970, 526, 970)
    p.note(1102, "Two account systems", ["Database credentials connect Java to MySQL. AquaTrack logins control", "the desktop session. Staff cannot open Reports or Accounts."], height=136)
    p.save()


def architecture():
    p = Plate("02-system-architecture.svg", "02", "How the Java application fits together", "Observed structure and the practical reason for each boundary.",
              "Main checks database connectivity and opens Swing login. UI panels coordinate user actions. Models carry records, utilities implement reusable rules and formatting, DAOs execute SQL through Database and MySQL Connector/J, and MySQL stores eight tables. UI role restrictions are separate from database privileges.", 1300)
    p.card(48, 175, 1004, 145, "1  Entry and sign-in", ["Main → database connectivity check → LoginFrame → MainFrame.", "Swing screens run on the event dispatch thread (EDT)."], fill=PALE)
    p.arrow(550, 325, 550, 348)
    p.card(48, 355, 1004, 170, "2  UI: forms, navigation and feedback", ["Dashboard • New Order • Deliveries • Containers & Balances",
          "Customers • Products & Prices • Inventory • Reports • Accounts",
          "Why: each screen groups the controls for one operator task."])
    p.arrow(300, 531, 300, 554)
    p.arrow(800, 531, 800, 554)
    p.card(48, 560, 484, 200, "model/ and util/", ["Models carry typed records.", "Rules, validation, money, passwords,", "session and receipts are reusable.", "Why: avoid repeating shared logic."])
    p.card(568, 560, 484, 200, "dao/", ["User, Customer, Product, Order,", "DeliveryStaff, Ledger, Report DAOs.", "SQL and transaction boundaries.", "Why: keep queries out of forms."])
    p.arrow(810, 766, 810, 789)
    p.card(48, 795, 1004, 145, "3  db/Database + MySQL Connector/J", ["Database opens JDBC connections; DAOs use prepared statements.", "Why: share connection setup and bind data separately from SQL."])
    p.arrow(550, 946, 550, 969)
    p.card(48, 975, 1004, 145, "4  MySQL / MariaDB: eight InnoDB tables", ["users • customers • products • delivery_staff • orders • order_items",
          "payments • container_ledger — durable records and foreign keys."], fill=PALE)
    p.note(1140, "Deployment boundary", ["This is a desktop JDBC system. UI roles do not replace database privileges."], fill=AMBER, height=88)
    p.save()


def transaction():
    p = Plate("03-order-transaction.svg", "03", "What happens when an order is saved", "OrderDao.placeOrder: one sale, one transaction, one receipt reference.",
              "The order transaction locks the active customer, locks and rereads product rows, checks availability and stock, reprices and deducts tracked stock, validates container returns, inserts order/items/ledger entries and commits. Any SQL or runtime failure rolls back the whole sale. Changed prices roll back and require review. Receipt loading occurs after commit and cannot undo the saved sale.", 1330)
    stages = [
        (175, "BEGIN", "Open connection; setAutoCommit(false).", "Reject an empty draft before starting."),
        (310, "LOCK", "Lock the active customer with SELECT … FOR UPDATE.", "This serializes order gallon checks for that customer."),
        (445, "RECHECK", "Lock each product; reread active flag, price and stock.", "Deduct Container / Cap/Seal stock; Refill is untracked."),
        (580, "VALIDATE", "A changed price aborts this save for operator review.", "Returned ≤ previous gallons out + borrowed now."),
        (715, "WRITE", "Insert orders, order_items and nonzero gallon movements.", "Store unit prices and line totals with the saved sale."),
        (850, "COMMIT", "Commit all sale changes together; return the order ID.", "Walk-in starts Delivered; Delivery starts Pending."),
    ]
    for y, label, line1, line2 in stages:
        p.rect(48, y, 1004, 116, PALE if label == "COMMIT" else WHITE)
        p.rect(66, y + 18, 146, 80, TEAL, TEAL, 12)
        p.text(139, y + 66, label, 24, WHITE, 700, "middle")
        p.lines(236, y + 44, [line1, line2], 24, spacing=36)
        if label != "COMMIT":
            p.arrow(550, y + 119, 550, y + 131)
    p.card(48, 990, 484, 192, "If a step fails", ["SQL / runtime error → rollback.", "No partial sale or stock deduction.", "Price change → review new total,", "then explicitly save again."], fill=AMBER)
    p.card(568, 990, 484, 192, "After commit: receipt", ["Reload the saved order by ID.", "Print through the system dialog.", "Receipt failure: retry that ID;", "do not enter the sale again."], fill=PALE)
    p.text(48, 1222, "Scope: standalone payments and returns do not share this transaction.", 24, MUTED)
    p.save()


def money():
    p = Plate("04-money-containers.svg", "04", "Sales, cash, stock and gallons are distinct", "Worked example: a new customer starts with zero debt and zero gallons out.",
              "Two refills at PHP25 produce a PHP50 Credit order. Borrowing two gallons gives two gallons out. Recording a PHP20 payment and one return leaves PHP30 unpaid and one gallon out; original credit sales remain PHP50. Inventory sold as products differs from borrowed containers. Delivery normally progresses Pending to Out for Delivery to Delivered; the current UI also permits Pending directly to Delivered without a rider.", 1370)
    p.card(48, 175, 1004, 145, "A  Save a Credit delivery order", ["2 refills × PHP 25.00 = PHP 50.00 sales; borrow 2, return 0.", "Debt becomes PHP 50.00. Gallons out becomes 2. Status: Pending."], fill=PALE)
    p.arrow(550, 325, 550, 348)
    p.card(48, 355, 1004, 145, "B  Later: record PHP 20.00 and one gallon returned", ["Unpaid balance = Credit order totals − recorded credit payments.", "Gallons out = all Borrowed quantities − all Returned quantities."])
    p.card(48, 525, 484, 165, "PHP 30.00 unpaid", ["PHP 50.00 − PHP 20.00.", "The original Credit sales total", "remains PHP 50.00."], fill=PALE)
    p.card(568, 525, 484, 165, "1 gallon out", ["2 borrowed − 1 returned.", "A return outside a sale is entered", "in Containers & Balances."], fill=PALE)
    p.card(48, 715, 484, 205, "Sellable stock", ["Containers and caps/seals lose", "stock when a sale is saved.", "Refills are untracked; they do", "not automatically consume caps."])
    p.card(568, 715, 484, 205, "Loaned containers", ["Gallons count customer loans.", "No effect on product stock", "or the charge for the order.", "Record a sale separately if sold."])
    p.text(48, 965, "DELIVERY STATUS: RECOMMENDED OPERATING PATH", 24, TEAL, 700)
    p.rect(48, 990, 250, 66, WHITE)
    p.rect(373, 990, 354, 66, WHITE)
    p.rect(802, 990, 250, 66, WHITE)
    p.text(173, 1032, "Pending", 27, NAVY, 700, "middle")
    p.text(550, 1032, "Out for Delivery", 27, NAVY, 700, "middle")
    p.text(927, 1032, "Delivered", 27, NAVY, 700, "middle")
    p.arrow(306, 1023, 365, 1023)
    p.arrow(735, 1023, 794, 1023)
    p.lines(48, 1098, ["Dispatch requires a rider. A delivered order cannot change riders.",
          "Current shortcut: Pending → Delivered is allowed, even without a rider.",
          "Delivery status never records a payment; collect Credit payments separately."], 24, spacing=35)
    p.note(1220, "Avoid double-counting", ["Cash orders are already paid in full; do not record another credit payment."], fill=AMBER, height=84)
    p.save()


def data_model():
    p = Plate("05-data-model.svg", "05", "Eight tables, one business record system", "Primary keys identify rows; foreign keys link records without duplicating them.",
              "Eight tables: users, customers, products, delivery_staff, orders, order_items, payments and container_ledger. Each order references one customer and creator, optionally a rider. Order items link orders to products and store saved prices. Payments reference customers rather than individual orders. Container movements reference customers and optionally orders. Foreign keys restrict parent deletion; archive records to retain their history.", 1370)
    entries = [
        (48, 175, "users", ["PK user_id; username; password hash", "Full name, role, active flag.", "App logins and order creators."]),
        (568, 175, "customers", ["PK customer_id; name; address", "Contact number; archived flag.", "Each sale needs a customer."]),
        (48, 365, "products", ["PK product_id; name; category", "Unit price, stock, reorder level.", "Active flag controls availability."]),
        (568, 365, "delivery_staff", ["PK staff_id; name; contact", "Active flag; independent of logins.", "An order may have no rider yet."]),
        (48, 555, "orders", ["PK order_id; customer; creator", "Optional rider; date; total", "Type, payment type, delivery status."]),
        (568, 555, "order_items", ["PK item_id; order_id; product_id", "Quantity; saved price; line total.", "Many lines can belong to one order."]),
        (48, 745, "payments", ["PK payment_id; customer_id", "Amount and payment date.", "Credit payments are customer-wide."]),
        (568, 745, "container_ledger", ["PK ledger_id; customer_id", "Borrowed / Returned; quantity; date", "Optional order_id links a sale."]),
    ]
    for x, y, heading, body in entries:
        p.card(x, y, 484, 170, heading, body)
    p.rect(48, 940, 1004, 266, PALE, PALE)
    p.text(72, 982, "RELATIONSHIPS  •  Parent 1 → many child records", 26, TEAL, 700)
    p.lines(72, 1029, ["customers → orders, payments, container_ledger",
          "users → orders (created_by); delivery_staff → orders (staff_id)",
          "orders → order_items, container_ledger (optional order link)",
          "products → order_items"], 24, spacing=38)
    p.note(1227, "History is retained", ["Foreign keys restrict parent deletion. Archive used customers or products."], fill=AMBER, height=83)
    p.save()


if __name__ == "__main__":
    operating()
    architecture()
    transaction()
    money()
    data_model()
