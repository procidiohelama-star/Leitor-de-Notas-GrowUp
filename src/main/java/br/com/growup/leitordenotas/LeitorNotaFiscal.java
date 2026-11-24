package br.com.growup.leitordenotas;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class LeitorNotaFiscal {

    private static final Pattern TOTAL = Pattern.compile(
            "(?i)(?:VALOR\\s+TOTAL(?:\\s+DA\\s+NOTA)?|TOTAL\\s+A\\s+PAGAR|VALOR\\s+A\\s+PAGAR)"
                    + "[^0-9]{0,100}(?:R\\$\\s*)?([0-9]+(?:[.\\s][0-9]{3})*(?:\\s*[,\\.]\\s*[0-9]{2})?)");
    private static final Pattern ACCESS_KEY = Pattern.compile("(?<!\\d)(?:\\d[ .-]?){44}(?!\\d)");
    private static final Pattern CPF = Pattern.compile(
            "(?i)CPF(?:\\s*(?:DO\\s+CONSUMIDOR)?\\s*[:\\-]?\\s*)(\\d{3}\\D?\\d{3}\\D?\\d{3}\\D?\\d{2})");
    private static final Pattern EMISSION_DATE = Pattern.compile(
            "(?i)(?:DATA\\s+DE\\s+EMISS[AÃ]O|EMISS[AÃ]O)\\D{0,15}(\\d{2}/\\d{2}/\\d{4})");

    @Value("${tesseract.datapath:./tessdata}")
    private String tessdataPath;

    public String extrairTexto(byte[] documento) throws IOException, TesseractException {
        BufferedImage imagem = ImageIO.read(new ByteArrayInputStream(documento));
        if (imagem == null) {
            throw new IllegalArgumentException("Arquivo inválido. Envie uma imagem JPG ou PNG legível.");
        }

        Tesseract leitor = new Tesseract();
        leitor.setDatapath(tessdataPath);
        leitor.setLanguage("por");
        return leitor.doOCR(imagem);
    }

    public Optional<BigDecimal> extrairValor(String texto) {
        Matcher matcher = TOTAL.matcher(texto);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String valorNormalizado = matcher.group(1)
                .replaceAll("\\s*,\\s*", ",")
                .replaceAll("\\s+", "");
        String valorJava;
        if (valorNormalizado.contains(",")) {
            valorJava = valorNormalizado.replace(".", "").replace(",", ".");
        } else {
            int ultimoPonto = valorNormalizado.lastIndexOf('.');
            boolean pontoDecimal = ultimoPonto >= 0
                    && valorNormalizado.length() - ultimoPonto - 1 == 2;
            valorJava = pontoDecimal
                    ? valorNormalizado.substring(0, ultimoPonto).replace(".", "")
                            + "." + valorNormalizado.substring(ultimoPonto + 1)
                    : valorNormalizado.replace(".", "");
        }
        BigDecimal valor = new BigDecimal(valorJava);
        return Optional.of(valor);
    }

    public Optional<String> extrairChaveAcesso(String texto) {
        Matcher matcher = ACCESS_KEY.matcher(texto);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group().replaceAll("\\D", ""));
    }

    public Optional<String> extrairCpfConsumidor(String texto) {
        Matcher matcher = CPF.matcher(texto);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(matcher.group(1).replaceAll("\\D", ""));
    }

    public Optional<String> extrairDataEmissao(String texto) {
        Matcher matcher = EMISSION_DATE.matcher(texto);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    public List<String> encontrarSinaisDeCancelamento(String texto) {
        String normalizado = texto.toUpperCase();
        List<String> sinais = new ArrayList<>();
        if (normalizado.contains("CANCELADA") || normalizado.contains("CANCELAMENTO")) {
            sinais.add("cancelada");
        }
        if (normalizado.contains("DENEGADA") || normalizado.contains("DENEGACAO")
                || normalizado.contains("DENEGAÇÃO")) {
            sinais.add("denegada");
        }
        return sinais;
    }

    public boolean cpfValido(String cpf) {
        if (cpf == null || !cpf.matches("\\d{11}") || cpf.chars().distinct().count() == 1) {
            return false;
        }
        int primeiro = calcularDigito(cpf.substring(0, 9), new int[]{10, 9, 8, 7, 6, 5, 4, 3, 2});
        int segundo = calcularDigito(cpf.substring(0, 10), new int[]{11, 10, 9, 8, 7, 6, 5, 4, 3, 2});
        return primeiro == Character.digit(cpf.charAt(9), 10)
                && segundo == Character.digit(cpf.charAt(10), 10);
    }

    private int calcularDigito(String base, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) {
            soma += Character.digit(base.charAt(i), 10) * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
