package br.com.growup.leitordenotas;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class HomeController {

    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final BigDecimal PERCENTUAL_DESCONTO = new BigDecimal("0.05");

    private final LeitorNotaFiscal leitor;
    private final JdbcTemplate jdbcTemplate;
    private final String dataInicioPrograma;
    private final String prazoValidadeDias;

    public HomeController(
            LeitorNotaFiscal leitor,
            JdbcTemplate jdbcTemplate,
            @Value("${programa.data-inicio:}") String dataInicioPrograma,
            @Value("${programa.prazo-validade-dias:}") String prazoValidadeDias) {
        this.leitor = leitor;
        this.jdbcTemplate = jdbcTemplate;
        this.dataInicioPrograma = dataInicioPrograma;
        this.prazoValidadeDias = prazoValidadeDias;
    }

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @PostMapping(path = "/api/notas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    @Transactional
    public AnaliseNota analisar(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam(value = "cpf", required = false) String cpfInformado) throws IOException {
        if (arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione uma imagem de nota fiscal.");
        }
        if (arquivo.getSize() > 10 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "O arquivo deve ter no máximo 10 MB.");
        }
        if (cpfInformado != null && !cpfInformado.isBlank()
                && !leitor.cpfValido(cpfInformado.replaceAll("\\D", ""))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O CPF informado é inválido.");
        }

       String texto;
        try {
                texto = leitor.extrairTexto(arquivo.getBytes());

                System.out.println("===== TEXTO EXTRAÍDO PELO OCR =====");
                System.out.println(texto);
                System.out.println("===== FIM DO TEXTO =====");

        } catch (TesseractException exception) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Não foi possível ler a imagem. Verifique se o pacote de idioma português do Tesseract está instalado.",
                exception);

        } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }

        Optional<BigDecimal> valor = leitor.extrairValor(texto);
        Optional<String> chave = leitor.extrairChaveAcesso(texto);
        Optional<String> cpfNota = leitor.extrairCpfConsumidor(texto);
        Optional<String> dataEmissao = leitor.extrairDataEmissao(texto);
        List<Regra> regras = new ArrayList<>();

        regras.add(new Regra("RN001", "Nota fiscal válida", Estado.PENDENTE,
                "A autorização precisa ser confirmada em uma fonte fiscal oficial; OCR não comprova autenticidade."));

        List<String> sinaisCancelamento = leitor.encontrarSinaisDeCancelamento(texto);
        regras.add(sinaisCancelamento.isEmpty()
                ? new Regra("RN002", "Nota cancelada ou denegada", Estado.PENDENTE,
                        "A situação atual da nota não pode ser consultada sem integração fiscal oficial.")
                : new Regra("RN002", "Nota cancelada ou denegada", Estado.REPROVADA,
                        "O texto contém indicação de nota " + String.join(" e ", sinaisCancelamento) + "."));

        if (chave.isEmpty()) {
            regras.add(new Regra("RN003", "Nota não duplicada", Estado.PENDENTE,
                    "Não foi possível identificar a chave de acesso de 44 dígitos."));
        } else if (jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notas_processadas WHERE chave_acesso = ?", Integer.class, chave.get()) > 0) {
            regras.add(new Regra("RN003", "Nota não duplicada", Estado.REPROVADA,
                    "Esta chave de acesso já foi usada para solicitar o benefício."));
        } else {
            regras.add(new Regra("RN003", "Nota não duplicada", Estado.PENDENTE,
                    "A chave será registrada após a aprovação das demais regras."));
        }

        regras.add(validarData(dataEmissao));

        regras.add(new Regra("RN005", "Integridade dos dados", Estado.PENDENTE,
                "A comparação com o XML original ou consulta fiscal ainda não está disponível."));

        if (cpfNota.isEmpty()) {
            regras.add(new Regra("RN006", "CPF do consumidor", Estado.NAO_APLICAVEL,
                    "A imagem não contém CPF do consumidor para comparação."));
        } else if (cpfInformado == null || cpfInformado.isBlank()) {
            regras.add(new Regra("RN006", "CPF do consumidor", Estado.PENDENTE,
                    "Informe seu CPF para comparar com o documento."));
        } else if (cpfNota.get().equals(cpfInformado.replaceAll("\\D", ""))) {
            regras.add(new Regra("RN006", "CPF do consumidor", Estado.VALIDA,
                    "O CPF informado corresponde ao CPF identificado na nota."));
        } else {
            regras.add(new Regra("RN006", "CPF do consumidor", Estado.REPROVADA,
                    "O CPF informado não corresponde ao documento."));
        }

        Optional<BigDecimal> descontoEstimado = valor.map(total ->
                total.multiply(PERCENTUAL_DESCONTO).setScale(2, java.math.RoundingMode.HALF_UP));
        boolean demaisRegrasValidas = regras.stream()
                .filter(regra -> !regra.id().equals("RN003"))
                .allMatch(regra -> regra.estado() == Estado.VALIDA || regra.estado() == Estado.NAO_APLICAVEL);
        if (demaisRegrasValidas && chave.isPresent() && regras.get(2).estado() == Estado.PENDENTE) {
            try {
                jdbcTemplate.update("INSERT INTO notas_processadas (chave_acesso) VALUES (?)", chave.get());
                regras.set(2, new Regra("RN003", "Nota não duplicada", Estado.VALIDA,
                        "Chave de acesso registrada para impedir novo benefício."));
            } catch (DuplicateKeyException exception) {
                regras.set(2, new Regra("RN003", "Nota não duplicada", Estado.REPROVADA,
                        "Esta chave de acesso já foi usada para solicitar o benefício."));
            }
        }
        boolean elegivel = descontoEstimado.isPresent()
                && regras.stream().allMatch(regra ->
                        regra.estado() == Estado.VALIDA || regra.estado() == Estado.NAO_APLICAVEL);

        return new AnaliseNota(valor.orElse(null), descontoEstimado.orElse(null), elegivel, regras,
                texto.isBlank() ? "Não foi possível extrair texto da imagem." : null);
    }

    private Regra validarData(Optional<String> dataTexto) {
        if (dataTexto.isEmpty()) {
            return new Regra("RN004", "Data de emissão", Estado.PENDENTE,
                    "Não foi possível identificar a data de emissão.");
        }
        if (dataInicioPrograma.isBlank() || prazoValidadeDias.isBlank()) {
            return new Regra("RN004", "Data de emissão", Estado.PENDENTE,
                    "Configure a data de início e o prazo de validade do programa.");
        }
        try {
            LocalDate emissao = LocalDate.parse(dataTexto.get(), DATA_BR);
            LocalDate inicio = LocalDate.parse(dataInicioPrograma);
            long diasPermitidos = Long.parseLong(prazoValidadeDias);
            if (diasPermitidos < 0) {
                throw new IllegalArgumentException("O prazo de validade não pode ser negativo.");
            }
            if (emissao.isBefore(inicio) || emissao.isAfter(LocalDate.now())
                    || emissao.isBefore(LocalDate.now().minusDays(diasPermitidos))) {
                return new Regra("RN004", "Data de emissão", Estado.REPROVADA,
                        "A emissão está fora do período permitido pelo programa.");
            }
            return new Regra("RN004", "Data de emissão", Estado.VALIDA,
                    "A data de emissão está dentro do período configurado.");
        } catch (DateTimeParseException | NumberFormatException exception) {
            throw new IllegalStateException("Configure programa.data-inicio (AAAA-MM-DD) e "
                    + "programa.prazo-validade-dias com valores válidos.", exception);
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<ErroResposta> tratarErro(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(new ErroResposta(exception.getReason() == null ? "Não foi possível analisar a nota." : exception.getReason()));
    }

    public enum Estado {
        VALIDA, REPROVADA, PENDENTE, NAO_APLICAVEL
    }

    public record Regra(String id, String titulo, Estado estado, String detalhe) {}

    public record AnaliseNota(
            BigDecimal valorNota,
            BigDecimal descontoEstimado,
            boolean beneficioLiberado,
            List<Regra> regras,
            String aviso) {}

    public record ErroResposta(String erro) {}
}
