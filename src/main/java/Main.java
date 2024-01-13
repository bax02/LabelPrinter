import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.awt.*;
import java.awt.print.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.awt.print.Paper;
import java.awt.print.PageFormat;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import org.apache.commons.csv.*;

public class Main {

    private static final JTextArea output = new JTextArea();
    private static final JTextArea input = new JTextArea();
    private static BufferedImage image;
    private static String barcode;



    public static class MyKeyListener implements KeyListener
    {

        @Override
        public void keyTyped(KeyEvent e) {
        }

        @Override
        public void keyPressed(KeyEvent e) {
        }

        @Override
        public void keyReleased(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                // Lock editing the input
                input.setEditable(false);

                //Get barcode from input and remove new line character
                barcode = input.getText();
                barcode = barcode.replace("\n", "");
                input.setText(null);

                // Create Barcode
                output.setText("Scanned Barcode: " + barcode + "\n");
                if (!GenerateQR(barcode))
                {
                    output.append("Error Generating QR Code, Try Scanning Again" + "\n");
                    input.setEditable(true);
                    output.append("Ready To Scan");
                    return;
                }
                output.append("Generated QR Code" + "\n");

                //Save QR Code for debugging
                File outputfile = new File("images/qrcode.png");
                try {
                    ImageIO.write(image, "png", outputfile);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }

                // Send Barcode to Printer
                output.append("Sending To Printer..." + "\n");
                try {
                    initPrint();
                } catch (PrinterException ex) {
                    output.append("Printing Failed, Check Connection" + "\n");
                }

                try {
                    addToCSV();
                } catch (IOException ex) {
                    output.append("Adding to CSV Failed" + "\n");
                }

                //Set the input as editable again and tell the user they can scan again
                input.setEditable(true);
                output.append("Ready To Scan");

            }
        }

        public void addToCSV() throws IOException {
            String CSV_FILE = "./log.csv";
            BufferedWriter writer = Files.newBufferedWriter(Paths.get(CSV_FILE), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            LocalDateTime now = LocalDateTime.now();
            String dateTime = dtf.format(now);
            String[] parts = dateTime.split(" ", 2);
            csvPrinter.printRecord(parts[0], parts[1], barcode);
            csvPrinter.flush();
        }


        public static class MyPrintable implements Printable {

            @Override
            public int print(Graphics graphics, PageFormat pageFormat,
                             int pageIndex) throws PrinterException {
                int result = NO_SUCH_PAGE;
                if (pageIndex == 0) {

                    graphics.translate((int)pageFormat.getImageableX(), (int)pageFormat.getImageableY());

                    result = PAGE_EXISTS;

                    graphics.drawImage(image, 0, 0, 60, 60, 0, 0, image.getHeight(), image.getWidth(),null);
                    graphics.setFont(new Font("Serif", Font.PLAIN, 7));
                    graphics.drawString(barcode, 60, 35);
                }
                return result;
            }
        }

        protected static double toPPI(double inch) {
            return inch * 72d;
        }

        protected static double fromCMToPPI(double cm) {
            return toPPI(cm * 0.393700787);
        }

        protected static String dump(Paper paper) {
            return paper.getWidth() + "x" + paper.getHeight() +
                    "/" + paper.getImageableX() + "x" +
                    paper.getImageableY() + " - " + paper
                    .getImageableWidth() +
                    "x" + paper.getImageableHeight();
        }

        protected static String dump(PageFormat pf) {
            Paper paper = pf.getPaper();
            return dump(paper);
        }

        private static void initPrint() throws PrinterException {
            PrinterJob pj = PrinterJob.getPrinterJob();
            PageFormat pf = pj.defaultPage();
            Paper paper = pf.getPaper();
            double width = fromCMToPPI(5);
            double height = fromCMToPPI(2.5);
            paper.setSize(width, height);
            paper.setImageableArea(
                    fromCMToPPI(0.1),
                    fromCMToPPI(0.1),
                    width - fromCMToPPI(0.1),
                    height - fromCMToPPI(0.1));
            System.out.println("Before- " + dump(paper));
            pf.setOrientation(PageFormat.PORTRAIT);
            pf.setPaper(paper);
            System.out.println("After- " + dump(paper));
            System.out.println("After- " + dump(pf));
            dump(pf);
            PageFormat validatePage = pj.validatePage(pf);
            System.out.println("Valid- " + dump(validatePage));
            pj.setPrintable(new MyPrintable(), pf);
            pj.print();
        }

    }

    public static boolean GenerateQR(String barcode)
    {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(new String(barcode.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8), BarcodeFormat.QR_CODE, 200, 200);
            image = MatrixToImageWriter.toBufferedImage(matrix);

        } catch (WriterException e) {
            return false;
        }
        return true;
    }

    public static void main(String[] args){
        JFrame frame = new JFrame("LabelPrinter");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);

        // Set the panel to add text boxes
        JPanel panel = new JPanel();
        BoxLayout boxlayout = new BoxLayout(panel, BoxLayout.Y_AXIS);
        panel.setLayout(boxlayout);
        panel.setPreferredSize(new Dimension(600, 120 + 180));

        // Set the image panel to add image
        JPanel imagePanel = new JPanel();
        BoxLayout imageBoxLayout = new BoxLayout(imagePanel, BoxLayout.Y_AXIS);
        imagePanel.setLayout(imageBoxLayout);
        imagePanel.setPreferredSize(new Dimension(600, 200));

        // Image
        BufferedImage bufferedImage;
        try {
            bufferedImage = ImageIO.read(new File("images/banner.png"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Image image = bufferedImage.getScaledInstance(600, 200, Image.SCALE_SMOOTH);
        JLabel imageLabel = new JLabel(new ImageIcon(image));
        imageLabel.setPreferredSize(new Dimension(600,200));



        // Output text
        output.setEditable(false);
        output.setFont(new Font(Font.DIALOG,  Font.PLAIN, 15));
        output.setBackground(Color.GRAY);
        output.setForeground(Color.BLACK);
        output.setPreferredSize(new Dimension(600, 120));
        output.setText("Ready To Scan");

        // Input text
        input.setPreferredSize(new Dimension(600, 180));
        input.setLineWrap(true);
        input.addKeyListener(new MyKeyListener());

        // Add components
        imagePanel.add(imageLabel);
        panel.add(output);
        panel.add(input);
        frame.add(imagePanel, BorderLayout.NORTH);
        frame.add(panel, BorderLayout.CENTER);
        frame.setSize(600, 600);
        frame.setVisible(true);
        output.setEditable(false);
        input.requestFocusInWindow();
    }
}
