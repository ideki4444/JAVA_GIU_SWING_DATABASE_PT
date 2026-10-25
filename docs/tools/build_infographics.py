#!/usr/bin/env python3
"""Rebuild editable, accessible vector learning plates; Python standard library only."""
from pathlib import Path
from html import escape
ROOT=Path(__file__).resolve().parents[2]
PLATES=[
('01-start-and-operate','Start here. Run a complete working day.',[
('01  PREPARE','JDK 25+ • Desktop • MySQL / MariaDB',['Build Aquatrack3-gpt-claude_1 from source.','Configure the JDBC URL, database user and password.','Properties override environment variables, then defaults.']),
('02  INITIALIZE','New database → schema → first owner',['Import schema.sql into an empty selected database.','Generate a PBKDF2 password hash; bootstrap one owner.','App logins and database credentials are separate.']),
('03  SET UP THE STATION','Products → Stock → Customers → Riders',['Owner sets pickup and delivery refill prices.','Receive stock for containers and caps/seals.','Create individual Staff / Owner/Admin login accounts.']),
('04  OPERATE AND REVIEW','Sell → Deliver → Collect → Reconcile',['Save each sale once, then preview / print its receipt.','Record credit collections and gallon returns separately.','Review sales, unpaid balances and stock; back up MySQL.'])]),
('02-sale-and-settlement','One sale. Four different measures.',[
('01  PRICE THE DRAFT','2 refills: pickup PHP 25 / delivery PHP 30',['Walk-in total: 2 × 25 = PHP 50.','Switch to Delivery: 2 × 30 = PHP 60.','Accessory products keep one unit price.']),
('02  SAVE ON CREDIT','PHP 60 owed • 2 gallons borrowed',['The delivery begins Pending; stock deducts at save.','Refills do not consume tracked stock automatically.','Borrowed gallons are loans, not sale charges.']),
('03  COLLECT AND RETURN','PHP 20 collected • 1 gallon returned',['Debt: PHP 60 − PHP 20 = PHP 40.','Gallons out: 2 − 1 = 1.','The original Credit sales value remains PHP 60.']),
('04  COMPLETE DELIVERY','Pending → Out for Delivery → Delivered',['Dispatch requires a rider; collection is a separate action.','Current code also permits Pending directly to Delivered.','Cash means already paid in full when the sale is saved.'])]),
('03-architecture','From a button to durable business records.',[
('01  WORKSPACE / UI','MainFrame • LoginPanel • Operational panels',['Screens capture input, selection and draft state.','UiTask / SwingWorker move database work off the EDT.','Busy and leave guards coordinate navigation and writes.']),
('02  MODELS / SHARED RULES','Product • OrderLine • Session • Validator',['Models carry named records and saved amounts.','BigDecimal computes currency; rules check business input.','Session notifications update the signed-in identity.']),
('03  DATA ACCESS','OrderDao • LedgerDao • UserDao • other DAOs',['Prepared statements bind values separately from SQL.','Transactions own row locks, validation and commit.','Database resolves settings and opens JDBC connections.']),
('04  PERSISTENCE / PRINT','Eight InnoDB tables • PrintDocument',['MySQL stores related sales, balances and catalog data.','PrintDocument shares snapshot layout with preview/print.','There is no HTTP server; each desktop uses JDBC.'])]),
('04-transactions-and-printing','Save first. Print the committed result.',[
('01  ORDER TRANSACTION','Lock customer → lock products → recheck',['Read current activity, stock and fulfillment-specific prices.','Changed price: roll back, show new total, require review.','Write order, lines, stock and loan movements; commit.']),
('02  PAYMENT / RETURN','Lock customer → recalculate → write → commit',['Reject overpayment or returns above current gallons out.','PaymentResult acknowledges the committed reference.','A later refresh failure does not mean payment failed.']),
('03  RECEIPT PREVIEW','80 mm roll • fitted length • wrapped details',['The sale commits before receipt loading or printing.','Zoom / Print / Close; no orientation or page navigation.','Retry the saved receipt ID rather than creating a sale again.']),
('04  REPORT PREVIEW','A4 • portrait / landscape • page navigation',['Preview and print share a captured document snapshot.','Canceling print changes no business transaction.','Printer drivers and actual paper handling need local tests.'])]),
('05-data-model','Eight tables explain the station.',[
('01  IDENTITIES / CATALOG','users • customers • products • delivery_staff',['Users sign in; riders are separate operational records.','Products store unit_price, delivery_price, stock and status.','Customer records carry contact/address and archive state.']),
('02  SALES','orders → order_items → products',['An order references customer, creator and optional rider.','Items retain quantity, saved unit price and line amount.','Customer/product descriptions are read from current rows.']),
('03  LATER ACTIVITY','customers → payments / container_ledger',['Payments reduce customer-level debt; no invoice allocation.','Ledger counts Borrowed / Returned; optional order link.','Timestamps are paid_at and moved_at respectively.']),
('04  RELATIONSHIPS / LIMITS','Keys connect records; calculations give balances',['Unique username, product name and customer name/contact.','Order-item parent deletion cascades; archive used records.','Schema constraints do not recompute all aggregate totals.'])])]

def build():
 out=ROOT/'docs/images';out.mkdir(parents=True,exist_ok=True)
 for name,title,cards in PLATES:
  parts=['<svg xmlns="http://www.w3.org/2000/svg" width="1100" height="1320" viewBox="0 0 1100 1320" role="img" aria-labelledby="title desc">',f'<title id="title">{escape(title)}</title>',f'<desc id="desc">{escape("; ".join(c[1]+": "+" ".join(c[2]) for c in cards))}</desc>','<rect width="1100" height="1320" fill="#f1f6f8"/>','<rect width="1100" height="170" fill="#102f43"/>']
  def text(x,y,value,size=26,color='#203e50',weight=400):parts.append(f'<text x="{x}" y="{y}" font-family="DejaVu Sans, sans-serif" font-size="{size}" font-weight="{weight}" fill="{color}">{escape(value)}</text>')
  text(45,45,'AQUATRACK3-GPT-CLAUDE_1  /  FIELD GUIDE',22,'#8ce0d5',700)
  text(45,100,title,34,'#ffffff',700)
  text(45,140,'Source-based instructions • Open the SVG to zoom • Read the full README',22,'#d2e4ed')
  for i,(label,heading,lines) in enumerate(cards):
   y=200+i*260
   parts.append(f'<rect x="40" y="{y}" width="1020" height="230" rx="18" fill="white" stroke="#cadde2"/>')
   text(65,y+39,label,23,'#087e82',700);text(65,y+82,heading,28,'#14374a',700)
   for j,line in enumerate(lines):text(65,y+125+j*35,line,25)
   if i<3: text(532,y+253,'↓',28,'#087e82',700)
  text(45,1280,'Java Swing • JDBC • MySQL   |   Source: uploaded Aquatrack3-gpt-claude_1',21,'#486b79')
  parts.append('</svg>');(out/(name+'.svg')).write_text('\n'.join(parts)+'\n')
  print(name+'.svg')
if __name__=='__main__':build()
