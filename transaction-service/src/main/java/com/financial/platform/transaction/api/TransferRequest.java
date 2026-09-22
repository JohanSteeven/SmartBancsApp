package com.financial.platform.transaction.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(

    @NotNull(message = "La cuenta de origen es obligatoria")
    UUID sourceAccountId,

    @NotNull(message = "La cuenta de destino es obligatoria")
    UUID destinationAccountId,

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor que cero")
    @Digits(integer = 16, fraction = 2, message = "El monto excede la precisión permitida (16 enteros, 2 decimales)")
    BigDecimal amount,

    @NotBlank(message = "La moneda es obligatoria")
    @Pattern(regexp = "^[A-Z]{3}$", message = "La moneda debe usar formato ISO-4217 de 3 letras (ej. USD)")
    String currency,

    @Size(max = 250, message = "La descripción no puede exceder 250 caracteres")
    String description
) {}
