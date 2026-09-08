from google.protobuf.internal import containers as _containers
from google.protobuf.internal import enum_type_wrapper as _enum_type_wrapper
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class OrderSide(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    ORDER_SIDE_UNSPECIFIED: _ClassVar[OrderSide]
    BUY: _ClassVar[OrderSide]
    SELL: _ClassVar[OrderSide]

class OrderType(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    ORDER_TYPE_UNSPECIFIED: _ClassVar[OrderType]
    LIMIT: _ClassVar[OrderType]
    MARKET: _ClassVar[OrderType]

class OrderStatus(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    ORDER_STATUS_UNSPECIFIED: _ClassVar[OrderStatus]
    PENDING: _ClassVar[OrderStatus]
    VALIDATED: _ClassVar[OrderStatus]
    PARTIALLY_FILLED: _ClassVar[OrderStatus]
    FILLED: _ClassVar[OrderStatus]
    CANCELLED: _ClassVar[OrderStatus]
    REJECTED: _ClassVar[OrderStatus]

class SignalAction(int, metaclass=_enum_type_wrapper.EnumTypeWrapper):
    __slots__ = ()
    SIGNAL_ACTION_UNSPECIFIED: _ClassVar[SignalAction]
    SIGNAL_BUY: _ClassVar[SignalAction]
    SIGNAL_SELL: _ClassVar[SignalAction]
    SIGNAL_HOLD: _ClassVar[SignalAction]
ORDER_SIDE_UNSPECIFIED: OrderSide
BUY: OrderSide
SELL: OrderSide
ORDER_TYPE_UNSPECIFIED: OrderType
LIMIT: OrderType
MARKET: OrderType
ORDER_STATUS_UNSPECIFIED: OrderStatus
PENDING: OrderStatus
VALIDATED: OrderStatus
PARTIALLY_FILLED: OrderStatus
FILLED: OrderStatus
CANCELLED: OrderStatus
REJECTED: OrderStatus
SIGNAL_ACTION_UNSPECIFIED: SignalAction
SIGNAL_BUY: SignalAction
SIGNAL_SELL: SignalAction
SIGNAL_HOLD: SignalAction

class Order(_message.Message):
    __slots__ = ("order_id", "user_id", "symbol", "side", "type", "quantity", "price", "timestamp_ms", "status")
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    USER_ID_FIELD_NUMBER: _ClassVar[int]
    SYMBOL_FIELD_NUMBER: _ClassVar[int]
    SIDE_FIELD_NUMBER: _ClassVar[int]
    TYPE_FIELD_NUMBER: _ClassVar[int]
    QUANTITY_FIELD_NUMBER: _ClassVar[int]
    PRICE_FIELD_NUMBER: _ClassVar[int]
    TIMESTAMP_MS_FIELD_NUMBER: _ClassVar[int]
    STATUS_FIELD_NUMBER: _ClassVar[int]
    order_id: str
    user_id: str
    symbol: str
    side: OrderSide
    type: OrderType
    quantity: float
    price: float
    timestamp_ms: int
    status: OrderStatus
    def __init__(self, order_id: _Optional[str] = ..., user_id: _Optional[str] = ..., symbol: _Optional[str] = ..., side: _Optional[_Union[OrderSide, str]] = ..., type: _Optional[_Union[OrderType, str]] = ..., quantity: _Optional[float] = ..., price: _Optional[float] = ..., timestamp_ms: _Optional[int] = ..., status: _Optional[_Union[OrderStatus, str]] = ...) -> None: ...

class Trade(_message.Message):
    __slots__ = ("trade_id", "buy_order_id", "sell_order_id", "symbol", "quantity", "price", "timestamp_ms")
    TRADE_ID_FIELD_NUMBER: _ClassVar[int]
    BUY_ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    SELL_ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    SYMBOL_FIELD_NUMBER: _ClassVar[int]
    QUANTITY_FIELD_NUMBER: _ClassVar[int]
    PRICE_FIELD_NUMBER: _ClassVar[int]
    TIMESTAMP_MS_FIELD_NUMBER: _ClassVar[int]
    trade_id: str
    buy_order_id: str
    sell_order_id: str
    symbol: str
    quantity: float
    price: float
    timestamp_ms: int
    def __init__(self, trade_id: _Optional[str] = ..., buy_order_id: _Optional[str] = ..., sell_order_id: _Optional[str] = ..., symbol: _Optional[str] = ..., quantity: _Optional[float] = ..., price: _Optional[float] = ..., timestamp_ms: _Optional[int] = ...) -> None: ...

class Signal(_message.Message):
    __slots__ = ("signal_id", "symbol", "action", "strategy_name", "strength", "suggested_price", "suggested_qty", "timestamp_ms")
    SIGNAL_ID_FIELD_NUMBER: _ClassVar[int]
    SYMBOL_FIELD_NUMBER: _ClassVar[int]
    ACTION_FIELD_NUMBER: _ClassVar[int]
    STRATEGY_NAME_FIELD_NUMBER: _ClassVar[int]
    STRENGTH_FIELD_NUMBER: _ClassVar[int]
    SUGGESTED_PRICE_FIELD_NUMBER: _ClassVar[int]
    SUGGESTED_QTY_FIELD_NUMBER: _ClassVar[int]
    TIMESTAMP_MS_FIELD_NUMBER: _ClassVar[int]
    signal_id: str
    symbol: str
    action: SignalAction
    strategy_name: str
    strength: float
    suggested_price: float
    suggested_qty: float
    timestamp_ms: int
    def __init__(self, signal_id: _Optional[str] = ..., symbol: _Optional[str] = ..., action: _Optional[_Union[SignalAction, str]] = ..., strategy_name: _Optional[str] = ..., strength: _Optional[float] = ..., suggested_price: _Optional[float] = ..., suggested_qty: _Optional[float] = ..., timestamp_ms: _Optional[int] = ...) -> None: ...

class Portfolio(_message.Message):
    __slots__ = ("user_id", "cash", "positions")
    USER_ID_FIELD_NUMBER: _ClassVar[int]
    CASH_FIELD_NUMBER: _ClassVar[int]
    POSITIONS_FIELD_NUMBER: _ClassVar[int]
    user_id: str
    cash: float
    positions: _containers.RepeatedCompositeFieldContainer[Position]
    def __init__(self, user_id: _Optional[str] = ..., cash: _Optional[float] = ..., positions: _Optional[_Iterable[_Union[Position, _Mapping]]] = ...) -> None: ...

class Position(_message.Message):
    __slots__ = ("symbol", "quantity", "average_cost", "current_pnl")
    SYMBOL_FIELD_NUMBER: _ClassVar[int]
    QUANTITY_FIELD_NUMBER: _ClassVar[int]
    AVERAGE_COST_FIELD_NUMBER: _ClassVar[int]
    CURRENT_PNL_FIELD_NUMBER: _ClassVar[int]
    symbol: str
    quantity: float
    average_cost: float
    current_pnl: float
    def __init__(self, symbol: _Optional[str] = ..., quantity: _Optional[float] = ..., average_cost: _Optional[float] = ..., current_pnl: _Optional[float] = ...) -> None: ...
