# Leitor de Notas Fiscais GrowUp

Aplicação web do Ciclo Verde para enviar uma imagem de nota fiscal, extrair informações com OCR e calcular uma estimativa de desconto de 5% sobre o valor total identificado.

## Funcionalidades

- Envio de imagens JPG ou PNG de até 10 MB, por seleção de arquivo ou arrastar e soltar.
- Leitura de texto em português usando Tesseract via Tess4J.
- Extração do valor total, data de emissão, chave de acesso e CPF do consumidor quando presentes.
- Cálculo do desconto estimado no servidor com `BigDecimal`; o navegador apresenta os valores em reais no formato `pt-BR`.
- Exibição das regras de análise com progresso e estados de aprovação, reprovação ou pendência.
- Persistência H2 de chaves de acesso para impedir o uso repetido da mesma nota depois que as regras aplicáveis forem aprovadas.

## Tecnologias

- **Java 21**
- **Spring Boot 3.4.4**: aplicação web, endpoint REST, upload e configuração.
- **Spring MVC e Thymeleaf**: controller e página inicial.
- **JavaScript, HTML e CSS**: formulário, progresso e resultado responsivo.
- **Tess4J 5.21.0 / Tesseract**: OCR em português.
- **JavaCV 1.5.14**: dependência de suporte a processamento de imagem.
- **Spring JDBC e H2**: armazenamento local das chaves de acesso.
- **Maven**: dependências, compilação e testes.

O arquivo de idioma português do Tesseract deve estar em `tessdata/por.traineddata`. O caminho padrão configurado é `./tessdata`.

## Pré-requisitos

- JDK 21 instalado e disponível no `PATH`.
- Apache Maven instalado e disponível como `mvn`.
- `tessdata/por.traineddata` presente no projeto.

Confira as instalações:

```powershell
java -version
mvn -version
```

## Como executar

Na raiz do repositório, compile e inicie a aplicação:

```powershell
mvn clean spring-boot:run
```

Abra [http://localhost:8080](http://localhost:8080) no navegador. Para encerrar, use `Ctrl+C` no terminal em que a aplicação está rodando.

## Testes

Execute os testes automatizados com:

```powershell
mvn test
```

## Regras e limitações da validação

A tela apresenta somente as regras ativas configuradas no servidor.

- **RN001 — Nota fiscal válida:** a autorização não é confirmada por uma consulta fiscal oficial; OCR, sozinho, não comprova autenticidade.
- **RN002 — Nota cancelada ou denegada:** o sistema identifica indicações textuais de cancelamento/denegação. Sem consulta oficial, não consegue confirmar que uma nota sem essas indicações continua autorizada.
- **RN003 — Nota não duplicada:** usa a chave de acesso de 44 dígitos e a tabela H2 `notas_processadas`.
- **RN004 — Data de emissão:** exige que o início do programa e o prazo permitido estejam configurados.
- **RN005 — Integridade dos dados:** a comparação com o XML original ou uma consulta fiscal ainda não está integrada.
- **RN006 — CPF do consumidor:** se a imagem tiver CPF, ele pode ser comparado ao CPF informado; se não tiver, a regra é não aplicável.

Para configurar a RN004, defina valores conforme a política real do programa em `src/main/resources/application.properties`:

```properties
programa.data-inicio=AAAA-MM-DD
programa.prazo-validade-dias=NUMERO_DE_DIAS
```

Enquanto houver regras pendentes ou reprovadas, o benefício não é liberado. O valor de 5% mostrado no cartão verde é uma estimativa até que as validações necessárias sejam concluídas.

## API de análise

O formulário envia uma requisição `POST /api/notas` como `multipart/form-data`:

- `arquivo`: imagem JPG ou PNG da nota fiscal.
- `cpf`: opcional; usado para comparação se a nota contiver CPF do consumidor.

A resposta inclui o valor da nota (`valorNota`), o desconto calculado (`descontoEstimado`), o estado de liberação (`beneficioLiberado`), as regras analisadas (`regras`) e um eventual aviso (`aviso`).

## Armazenamento local

O H2 usa o arquivo `growup.mv.db` na raiz de execução do projeto. A tabela `notas_processadas` é criada a partir de `src/main/resources/schema.sql`.

## Estrutura principal

```text
src/main/java/br/com/growup/leitordenotas/
  HomeController.java             # Página, endpoint e regras de negócio
  LeitorNotaFiscal.java           # OCR e extração dos campos da nota
  LeitorDeNotasApplication.java   # Inicialização Spring Boot
src/main/resources/
  templates/index.html            # Página do formulário e cartão de benefício
  static/css/style.css             # Estilos responsivos
  static/js/invoice-upload.js      # Upload, progresso e apresentação do resultado
  application.properties          # Configurações do aplicativo e banco
  schema.sql                       # Esquema H2
src/test/java/                     # Testes automatizados
tessdata/por.traineddata           # Modelo OCR em português
```
