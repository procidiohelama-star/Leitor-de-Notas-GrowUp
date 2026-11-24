package br.com.growup.leitordenotas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LeitorNotaFiscalTest {

    private final LeitorNotaFiscal leitor = new LeitorNotaFiscal();

    @Test
    void extraiValorDaNotaEmFormatoBrasileiro() {
        assertEquals("1234.56", leitor.extrairValor("VALOR TOTAL DA NOTA R$ 1.234,56")
                .orElseThrow().toPlainString());
    }

    @Test
    void extraiValorMesmoComEspacosAoRedorDaVirgula() {
        assertEquals("120.50", leitor.extrairValor("VALOR TOTAL DA NOTA R$ 120 , 50")
                .orElseThrow().toPlainString());
    }

    @Test
    void extraiValorComSeparadorDecimalPontoOuMilharComEspacos() {
        assertEquals("120.50", leitor.extrairValor("TOTAL A PAGAR: R$ 120.50")
                .orElseThrow().toPlainString());
        assertEquals("1234567.89", leitor.extrairValor("VALOR TOTAL: 1 234 567,89")
                .orElseThrow().toPlainString());
    }

    @Test
    void extraiTotalDoFormatoDoCupomFiscal() {
        String texto = "VALOR TOTAL R$\n471,72\nFORMA DE PAGAMENTO\nPIX\n471,72";

        assertEquals("471.72", leitor.extrairValor(texto).orElseThrow().toPlainString());
    }

    @Test
    void extraiTotalMesmoComEspacosEntreRotuloEValor() {
        String texto = "VALOR TOTAL R$" + " ".repeat(40) + "471,72";

        assertEquals("471.72", leitor.extrairValor(texto).orElseThrow().toPlainString());
    }

    @Test
    void validaDigitosVerificadoresDeCpf() {
        assertTrue(leitor.cpfValido("52998224725"));
        assertFalse(leitor.cpfValido("52998224724"));
    }

    @Test
    void extraiChaveDeAcessoMesmoComSeparadores() {
        String chave = "12345678901234567890123456789012345678901234";
        assertEquals(chave, leitor.extrairChaveAcesso("Chave: " + chave).orElseThrow());
    }

    @Test
    void reconheceDataRotuladaEIndicacaoDeCancelamento() {
        assertEquals("21/11/2026",
                leitor.extrairDataEmissao("DATA DE EMISSÃO: 21/11/2026").orElseThrow());
        assertTrue(leitor.encontrarSinaisDeCancelamento("NF-e CANCELADA").contains("cancelada"));
        assertTrue(leitor.encontrarSinaisDeCancelamento("NF-e autorizada").isEmpty());
    }
}
