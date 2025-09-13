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
- **Problema:** cada tipo (EMAIL, PHONE, CPF, CNPJ, RANDOM) tem regras próprias.
- **Solução:** uma *Strategy* por tipo, entregue via *Factory*.
- **Benefícios:**
  - Evita `if/switch` no service.
  - Aplica OCP (novo tipo sem tocar nos outros).
  - Regras testáveis isoladamente.
- 🔗 Referência: [Refactoring Guru](https://refactoring.guru)

---

### 2. Specification-like (Criteria)
- **Problema:** filtros variáveis (tipo, agência+conta, nome, datas).
- **Solução:** Query com `Criteria` via `MongoTemplate`.
- **Benefícios:**
  - Regras centralizadas.
  - Reuso e legibilidade sem “spaghetti” no repositório.

---

### 3. Problem Details (RFC-7807)
- **400:** validação (Bean Validation).
- **404:** recurso inexistente.
- **422:** violações de negócio (duplicidade, limite, chave inativa).
- **Benefício:** respostas padronizadas e previsíveis.

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
