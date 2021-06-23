package com.rbkmoney.registry.payout.worker.reader;

import com.rbkmoney.registry.payout.worker.exception.RegistryPayoutWorkerException;
import com.rbkmoney.registry.payout.worker.model.Transactions;
import com.rbkmoney.registry.payout.worker.parser.RegistryParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FtpTransactionsReader {

    private final List<RegistryParser> parsers;
    private static final String PATH_TO_PROCESSED_FILE = "processed";

    public Transactions readDirectories(FTPClient ftpClient) throws IOException {
        Transactions transactions = new Transactions();
        FTPFile[] ftpDirs = ftpClient.listDirectories();
        for (FTPFile ftpDir : ftpDirs) {
            if (ftpDir.getName().equals(".") || ftpDir.getName().equals("..")) {
                continue;
            }
            ftpClient.changeWorkingDirectory(ftpDir.getName());
            transactions.addAll(readFiles(ftpClient, ftpDir.getName()));
            ftpClient.changeToParentDirectory();
        }
        return transactions;
    }

    private Transactions readFiles(FTPClient ftpClient, String pathDir) throws IOException {
        Transactions transactions = new Transactions();
        FTPFile[] ftpFiles = ftpClient.listFiles();
        for (FTPFile ftpFile : ftpFiles) {
            if (ftpFile.isFile()) {
                InputStream inputStream = ftpClient.retrieveFileStream(ftpFile.getName());
                if (ftpClient.completePendingCommand()) {
                    log.info("File {} was received successfully.", ftpFile.getName());
                }
                Transactions fileTransactions = parsers.stream()
                        .filter(parser -> parser.isParse(pathDir))
                        .findFirst()
                        .orElseThrow(RegistryPayoutWorkerException::new)
                        .parse(inputStream);
                transactions.addAll(fileTransactions);
                inputStream.close();
                ftpClient.makeDirectory(PATH_TO_PROCESSED_FILE);
                ftpClient.rename(ftpClient.printWorkingDirectory() + "/" + ftpFile.getName(),
                        ftpClient.printWorkingDirectory() + "/" + PATH_TO_PROCESSED_FILE + "/" + ftpFile.getName());
            }
        }
        return transactions;
    }

}