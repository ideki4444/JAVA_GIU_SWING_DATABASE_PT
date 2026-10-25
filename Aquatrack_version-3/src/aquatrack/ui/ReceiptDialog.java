package aquatrack.ui;

import aquatrack.model.CustomerOrder;
import aquatrack.util.Receipt;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Window;
import java.awt.print.PrinterException;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/** Shows a sale's receipt as monospaced text, with Print (Java's print dialog) and Close. */
public final class ReceiptDialog extends JDialog {

    private ReceiptDialog(Window owner, CustomerOrder order) {
        super(owner, "Receipt #" + order.getId(), Dialog.ModalityType.APPLICATION_MODAL);
        JTextArea text = new JTextArea(Receipt.format(order));
        text.setEditable(false);
        text.setFont(new Font("Monospaced", Font.PLAIN, 13));
        text.setMargin(new Insets(12, 12, 12, 12));

        UiKit.ThemedButton print = UiKit.button("Print", UiKit.ACCENT);
        print.addActionListener(e -> {
            try {
                text.print();
            } catch (PrinterException ex) {
                UiKit.warn(this, "The receipt could not be printed: " + ex.getMessage());
            }
        });
        UiKit.ThemedButton close = UiKit.button("Close", UiKit.SIDEBAR);
        close.addActionListener(e -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(print);
        buttons.add(close);
        setLayout(new BorderLayout());
        add(new JScrollPane(text), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Opens the receipt for a saved sale over the window that is showing. */
    public static void showReceipt(Component parent, CustomerOrder order) {
        if (order == null) {
            return;
        }
        new ReceiptDialog(SwingUtilities.getWindowAncestor(parent), order).setVisible(true);
    }
}
