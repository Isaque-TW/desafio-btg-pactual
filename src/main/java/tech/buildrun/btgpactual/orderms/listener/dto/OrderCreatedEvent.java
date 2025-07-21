package tech.buildrun.btgpactual.orderms.listener.dto;

import java.util.List;

public record OrderCreatedEvent(
        Long codigoPedido,
        Long codigoClient,  // <<< esse nome aqui!
        List<OrderItemEvent> itens
) {}
