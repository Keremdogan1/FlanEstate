package com.example.secretid;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class CloudDataFetcher {

    private static List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                values.add(sb.toString().trim());
                sb = new StringBuilder();
            } else {
                sb.append(c);
            }
        }
        values.add(sb.toString().trim());
        return values;
    }

    private static String normalizeDate(String s) {
        if (s == null) return "";
        return s.toLowerCase()
                .replace("-", "")
                .replace(" ", "")
                .replace("ş", "s")
                .replace("ğ", "g")
                .replace("ı", "i")
                .replace("ü", "u")
                .replace("ö", "o")
                .replace("ç", "c")
                .replace("mart", "mar")
                .replace("nisan", "nis")
                .replace("mayis", "may")
                .replace("haziran", "haz")
                .replace("temmuz", "tem")
                .replace("agustos", "agu")
                .replace("eylul", "eyl")
                .replace("ekim", "eki")
                .replace("kasim", "kas")
                .replace("aralik", "ara")
                .replace("ocak", "oca")
                .replace("subat", "sub");
    }

    public static void fetchData(ServerPlayerEntity player, String targetDate) {
        new Thread(() -> {
            try {
                String csvUrl = "https://docs.google.com/spreadsheets/d/e/2PACX-1vQB8dZF_yf_mXk30mAw2uCgj2sltSXrrv0-IED8Zr47ibajCyFN9R_pDYADFGgOTAPeyXowMtcg3AG8/pub?gid=11840969&single=true&output=csv";
                URL url = new URL(csvUrl);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                List<List<String>> rows = new ArrayList<>();
                boolean startReading = false;
                
                while ((inputLine = in.readLine()) != null) {
                    if (inputLine.startsWith("Veriler,")) {
                        startReading = true;
                    }
                    if (startReading && !inputLine.trim().isEmpty()) {
                        rows.add(parseCsvLine(inputLine));
                        if (rows.size() == 21) break; // Veriler + 20 data rows
                    }
                }
                in.close();
                con.disconnect();

                if (rows.size() >= 21) {
                    List<String> header = rows.get(0);
                    List<String> firstData = rows.get(1); // Enflasyon row
                    
                    int targetIdx = -1;
                    
                    if (targetDate != null && !targetDate.isEmpty()) {
                        String normalizedTarget = normalizeDate(targetDate);
                        for (int i = 1; i < header.size(); i++) {
                            if (normalizeDate(header.get(i)).equals(normalizedTarget)) {
                                targetIdx = i;
                                break;
                            }
                        }
                    } else {
                        // Find the last column index that has data
                        for (int i = firstData.size() - 1; i >= 1; i--) {
                            if (i < firstData.size() && !firstData.get(i).isEmpty()) {
                                targetIdx = i;
                                break;
                            }
                        }
                    }
                    
                    if (targetIdx != -1 && targetIdx < header.size()) {
                        String date = header.get(targetIdx);
                        player.sendMessage(Text.literal("§b--- " + date + " EKONOMI VERILERI ---"), false);
                        
                        String[] labels = {
                            "Enflasyon", "Yatirim", "Ozel Tuketim", "Buyume", "Faiz",
                            "Ithalat", "Ihracat", "Harcamalar", "Zamana Yetisme", "Net Rezerv",
                            "Rezerv Degisikligi", "Issizlik", "Vergi Gelirleri", "Faiz Gelirleri",
                            "Diger Gelirler", "Dis Yatirim", "Net Gelir", "Net Gider", "Cari Denge", "Net Deger"
                        };
                        
                        for (int r = 1; r <= 20; r++) {
                            List<String> row = rows.get(r);
                            String value = targetIdx < row.size() ? row.get(targetIdx) : "Veri Yok";
                            player.sendMessage(Text.literal("§a" + labels[r-1] + ": §e" + value), false);
                        }
                    } else {
                        if (targetDate != null && !targetDate.isEmpty()) {
                            player.sendMessage(Text.literal("§c" + targetDate + " tarihi icin veri tablosunda sonuc bulunamadi."), false);
                        } else {
                            player.sendMessage(Text.literal("§cVeri tablosunda gecerli sutun bulunamadi."), false);
                        }
                    }
                } else {
                    player.sendMessage(Text.literal("§cTablo eksik okundu veya bos."), false);
                }

            } catch (Exception e) {
                player.sendMessage(Text.literal("§cBulut verisi alinirken hata olustu: " + e.getMessage()), false);
                e.printStackTrace();
            }
        }).start();
    }
    
    public static void fetchExchangeRateData(ServerPlayerEntity player) {
        new Thread(() -> {
            try {
                String csvUrl = "https://docs.google.com/spreadsheets/d/e/2PACX-1vQB8dZF_yf_mXk30mAw2uCgj2sltSXrrv0-IED8Zr47ibajCyFN9R_pDYADFGgOTAPeyXowMtcg3AG8/pub?gid=1800121183&single=true&output=csv";
                URL url = new URL(csvUrl);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                List<List<String>> rows = new ArrayList<>();
                boolean startReading = false;
                
                while ((inputLine = in.readLine()) != null) {
                    if (!startReading && (inputLine.startsWith("Döviz") || inputLine.startsWith("Doviz") || inputLine.contains("1/1/2026"))) {
                        startReading = true;
                    }
                    if (startReading && !inputLine.trim().isEmpty()) {
                        rows.add(parseCsvLine(inputLine));
                        if (rows.size() == 7) break; // Header + 6 data rows
                    }
                }
                in.close();
                con.disconnect();

                if (rows.size() >= 7) {
                    List<String> header = rows.get(0);
                    List<String> firstData = rows.get(1); // Dolar row
                    
                    int lastIdx = -1;
                    for (int i = firstData.size() - 1; i >= 1; i--) {
                        if (i < firstData.size() && !firstData.get(i).isEmpty()) {
                            lastIdx = i;
                            break;
                        }
                    }
                    
                    if (lastIdx != -1 && lastIdx < header.size()) {
                        String date = header.get(lastIdx);
                        player.sendMessage(Text.literal("§b>>> " + date + " DOVIZ KURLARI <<<"), false);
                        
                        String[] labels = {
                            "Dolar", "Euro", "Sterlin", "50 Turk Lirasi", "Frank", "Kredi Risk Primi"
                        };
                        
                        for (int r = 1; r <= 6; r++) {
                            List<String> row = rows.get(r);
                            String value = lastIdx < row.size() ? row.get(lastIdx) : "Veri Yok";
                            player.sendMessage(Text.literal("§a" + labels[r-1] + ": §e" + value), false);
                        }
                    } else {
                        player.sendMessage(Text.literal("§cDoviz tablosunda gecerli sutun bulunamadi."), false);
                    }
                } else {
                    player.sendMessage(Text.literal("§cDoviz tablosu eksik okundu veya bos."), false);
                }

            } catch (Exception e) {
                player.sendMessage(Text.literal("§cDoviz verisi alinirken hata olustu: " + e.getMessage()), false);
                e.printStackTrace();
            }
        }).start();
    }
}
