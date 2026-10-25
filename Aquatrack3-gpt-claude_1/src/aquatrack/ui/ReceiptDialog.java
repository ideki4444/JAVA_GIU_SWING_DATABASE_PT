package aquatrack.ui;

import aquatrack.model.CustomerOrder;
import aquatrack.util.Receipt;
import java.awt.Component;

/** Opens a structured receipt preview for an already saved sale. */
public final class ReceiptDialog {
    private ReceiptDialog() { }

    /** Opens the receipt for a saved sale over the window that is showing. */
    public static void showReceipt(Component parent, CustomerOrder order) {
        if (order != null) PrintPreviewDialog.showPreview(parent, Receipt.document(order));
    }
}
