# API de Chaves PIX

API REST para **cadastro**, **consulta**, **alteração de conta** e **inativação** de **chaves PIX**.

- **Spring Boot 3** (Web + Validation)
- **Spring Data MongoDB**
- **springdoc-openapi** (Swagger UI)
- **JUnit 5 + Mockito** (unit), **Testcontainers** (integração com Mongo real)
- **JaCoCo** (cobertura: ~98% linhas, ≥90% branches)
________________________________________________________________________________________________________

## Como rodar

### Pré-requisitos
- **Java 21**
- **Docker** (Desktop/Engine) – necessário para os testes de integração (Testcontainers)
- **Maven wrapper** (`./mvnw`) já incluso no projeto
- *(Opcional)* **MongoDB Compass** – para inspecionar o banco em desenvolvimento

### Dev (subir app + Mongo local)

### 1) Banco local
```bash
cd local
docker compose up -d
```

### 2) Aplicação 
```bash
cd ..
./mvnw spring-boot:run
```

### Todos os testes (unit + integração com Testcontainers)
```bash
./mvnw test
```

### Rodar e abrir relatório de cobertura (JaCoCo) 
```bash
./mvnw verify target/site/jacoco/index.html
```

### Acesso para API no OpenAPI - Swagger:
https://localhost:8080/swagger-ui/index.html

#### Dica (MongoDB Compass)

Após `docker compose up -d` em `local/`, conecte no Compass com:
- Connection: `mongodb://localhost:27017`
- Authentication
- Username: admin
- Password: 123
__________________________________________________________________________

## Decisões de Projeto

### 1. Strategy + Factory (validação por tipo de chave)

- **Contexto:** cada tipo de chave (EMAIL, PHONE, CPF, CNPJ, RANDOM) possui regras de validação próprias.
- **Problema:** sem uma estratégia clara, seria necessário um `if/switch` no `service`, o que viola princípios de design (acoplamento alto e difícil manutenção).
- **Escolha:** optei pelo **Design Pattern Strategy**, onde cada tipo de chave tem seu validador especializado, entregue por uma **Factory**.
- **Por que essa estratégia?**
  - Facilita **extensão**: adicionar um novo tipo não exige mexer nos outros (`OCP` – Open/Closed Principle).
  - Permite **testabilidade isolada**: cada validador é testado de forma independente.
  - Evita código procedural espalhado no `service`.
- 🔗 **Referência:** [Refactoring Guru – Strategy Pattern](https://refactoring.guru/design-patterns/strategy)

---

## 2. Banco de Dados — MongoDB (por quê?)

**Contexto.** O domínio de PixKey é naturalmente “document-like”: os JSONs de entrada/saída mapeiam 1-para-1 para a entidade persistida e não há necessidade de *joins* entre múltiplas tabelas.

**Motivação.**
- **Modelo orientado a documento (JSON-first):** a estrutura dos dados da API (JSON) é salva de forma quase idêntica no banco de dados, eliminando a necessidade de tradução entre eles.
- **Schema flexível:** adicionamos campos no tempo sem migrações SQL (mudanças são tratadas no domínio/DTOs).
- **Índices simples e eficientes:** 
  - **Unicidade global da chave** (`keyValue`) garantida por índice único.
  - **Consultas frequentes** (ex.: por `agency+account`, `status`) atendidas com índices compostos.
- **Developer-experience:** Spring Data MongoDB + Docker/Testcontainers tornam o *setup* local e os testes de integração limpos e simples de implementar.

**Trade-offs.**
- **Sem `JpaSpecificationExecutor`:** o Spring Data MongoDB não tem Specification nativo.
  - **Mitigação:** usamos um **Specification-like** com `MongoTemplate` + `Criteria` (seção 3).
- **Sem *joins* relacionais:** modelagem deve favorecer *aggregation by document*

**Modelagem & Índice**
- Índice único para `keyValue` (garante regra de unicidade global).
- Índice composto para consultas por conta/estado:
  - `{ agency: 1, account: 1, status: 1 }` (ajuda nas telas/relatórios por conta).

## 3. Specification-like (Criteria para consultas combináveis)

- **Contexto:** consultas precisam de filtros variáveis (tipo, agência+conta, nome, datas).
- **Problema:** no **JPA** existe `JpaSpecificationExecutor` para compor filtros, mas no **MongoDB** não há suporte nativo ao padrão Specification.
- **Escolha:** implementar uma abordagem *Specification-like* usando `Criteria` do `MongoTemplate`.
- **Por que essa estratégia?**
  - Permite **composição dinâmica** de filtros (como no padrão Specification).
  - Centraliza regras de filtragem, aumentando **clareza e reuso**.
  - Evita “spaghetti” de `if/else` para montar queries no repositório.
- **Benefício adicional:** mantemos a ideia de *Specification Pattern* do DDD e do [Refactoring Guru](https://refactoring.guru/design-patterns/specification), mas adaptada ao ecossistema MongoDB.


---

____________________________________________________________________________________________________
### Quando cada status é retornado

#### POST `/pix-keys`
- **201 Created** – chave criada com sucesso. Retorna header **Location** com `/pix-keys/{id}`.
- **400 Bad Request** – erro de **Bean Validation** / payload inválido:
  - campos obrigatórios ausentes ou em branco (`keyType`, `keyValue`, `accountType`, `agency`, `account`, `holderName`);
  - `agency` != 4 dígitos ou `account` != 8 dígitos;
  - enums inválidos (ex.: `accountType` desconhecido), JSON malformado.
- **422 Unprocessable Entity** – **regra de negócio** violada:
  - `keyValue` já cadastrado para outro correntista;
  - limite de chaves por conta atingido (>= **5** para `agency+account`);
  - valor rejeitado pelo **validador do tipo** (e-mail/telefone/CPF/CNPJ/RANDOM).  
    > `RANDOM` deve ser **alfanumérico de 32 caracteres**.

#### GET `/pix-keys/{id}`
- **200 OK** – chave encontrada.
- **404 Not Found** – id inexistente.

#### PATCH `/pix-keys/{id}/inactivate`
- **204 No Content** – inativação concluída (soft delete).
- **404 Not Found** – id inexistente.
- **422 Unprocessable Entity** – chave **já está inativa**.

#### PUT `/pix-keys/{id}/account`
- **200 OK** – conta atualizada.  
  > Se `agency+account` permanecerem os mesmos, **não** há checagem de limite.
- **400 Bad Request** – erro de **Bean Validation** do corpo (ex.: `agency`/`account` com tamanho inválido, `holderName` em branco).
- **404 Not Found** – id inexistente.
- **422 Unprocessable Entity** – **regra de negócio** violada:
  - chave está **inativa**;
  - ao mover para **outra** conta, o destino já possui **>= 5** chaves.

> **Observação:** a API **não expõe `DELETE`**. A remoção lógica é feita via **PATCH `/inactivate`**. Uma chamada `DELETE /pix-keys/{id}` resultará em **405 Method Not Allowed**.

Acesse:

Swagger UI: http://localhost:8080/swagger-ui/index.html
