package com.peterungab.sms.ui.views;

import com.peterungab.sms.ui.MainFrame;
import com.peterungab.sms.ui.Prompts;
import com.peterungab.sms.ui.Ui;
import com.peterungab.sms.util.CsvWriter;

import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** File chooser + CSV writing shared by the table pages. */
final class Exports {

    private static File lastDirectory;

    private Exports() {
    }

    static Optional<Path> chooseCsvFile(MainFrame frame, String baseName) {
        File suggested = new File(lastDirectory, baseName + "-" + LocalDate.now() + ".csv");
        Optional<File> chosen = Prompts.chooseSaveFile(frame, "Export to CSV", suggested,
                new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        if (chosen.isEmpty()) {
            return Optional.empty();
        }
        File file = chosen.get();
        lastDirectory = file.getParentFile();
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            file = new File(file.getParentFile(), file.getName() + ".csv");
        }
        Path path = file.toPath();
        if (Files.exists(path) && !Ui.confirm(frame, "Replace file",
                file.getName() + " already exists. Replace it?", "Replace")) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    static void write(MainFrame frame, Path file, List<String> header, List<List<String>> rows) {
        try {
            CsvWriter.write(file, header, rows);
            frame.statusBar().flash("Exported " + rows.size() + " row(s) to " + file.getFileName());
        } catch (IOException e) {
            Ui.showError(frame, "Export failed", "Could not write " + file + ":\n" + e.getMessage());
        }
    }
}
