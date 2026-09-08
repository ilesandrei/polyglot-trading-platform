import trading_pb2 as _trading_pb2
from google.protobuf.internal import containers as _containers
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from collections.abc import Iterable as _Iterable, Mapping as _Mapping
from typing import ClassVar as _ClassVar, Optional as _Optional, Union as _Union

DESCRIPTOR: _descriptor.FileDescriptor

class SubmitOrderRequest(_message.Message):
    __slots__ = ("order",)
    ORDER_FIELD_NUMBER: _ClassVar[int]
    order: _trading_pb2.Order
    def __init__(self, order: _Optional[_Union[_trading_pb2.Order, _Mapping]] = ...) -> None: ...

class SubmitOrderResponse(_message.Message):
    __slots__ = ("order_id", "status", "message")
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    STATUS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    order_id: str
    status: _trading_pb2.OrderStatus
    message: str
    def __init__(self, order_id: _Optional[str] = ..., status: _Optional[_Union[_trading_pb2.OrderStatus, str]] = ..., message: _Optional[str] = ...) -> None: ...

class CancelOrderRequest(_message.Message):
    __slots__ = ("order_id", "user_id")
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    USER_ID_FIELD_NUMBER: _ClassVar[int]
    order_id: str
    user_id: str
    def __init__(self, order_id: _Optional[str] = ..., user_id: _Optional[str] = ...) -> None: ...

class CancelOrderResponse(_message.Message):
    __slots__ = ("success", "message")
    SUCCESS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    success: bool
    message: str
    def __init__(self, success: _Optional[bool] = ..., message: _Optional[str] = ...) -> None: ...

class StreamExecutionsRequest(_message.Message):
    __slots__ = ("symbol",)
    SYMBOL_FIELD_NUMBER: _ClassVar[int]
    symbol: str
    def __init__(self, symbol: _Optional[str] = ...) -> None: ...

class PlaceOrderRequest(_message.Message):
    __slots__ = ("order",)
    ORDER_FIELD_NUMBER: _ClassVar[int]
    order: _trading_pb2.Order
    def __init__(self, order: _Optional[_Union[_trading_pb2.Order, _Mapping]] = ...) -> None: ...

class PlaceOrderResponse(_message.Message):
    __slots__ = ("order_id", "status", "message")
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    STATUS_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    order_id: str
    status: _trading_pb2.OrderStatus
    message: str
    def __init__(self, order_id: _Optional[str] = ..., status: _Optional[_Union[_trading_pb2.OrderStatus, str]] = ..., message: _Optional[str] = ...) -> None: ...

class GetPortfolioRequest(_message.Message):
    __slots__ = ("user_id",)
    USER_ID_FIELD_NUMBER: _ClassVar[int]
    user_id: str
    def __init__(self, user_id: _Optional[str] = ...) -> None: ...

class GetPortfolioResponse(_message.Message):
    __slots__ = ("portfolio",)
    PORTFOLIO_FIELD_NUMBER: _ClassVar[int]
    portfolio: _trading_pb2.Portfolio
    def __init__(self, portfolio: _Optional[_Union[_trading_pb2.Portfolio, _Mapping]] = ...) -> None: ...

class StreamTradesRequest(_message.Message):
    __slots__ = ("user_id", "symbol")
    USER_ID_FIELD_NUMBER: _ClassVar[int]
    SYMBOL_FIELD_NUMBER: _ClassVar[int]
    user_id: str
    symbol: str
    def __init__(self, user_id: _Optional[str] = ..., symbol: _Optional[str] = ...) -> None: ...

class StreamSignalsRequest(_message.Message):
    __slots__ = ("symbols", "strategy")
    SYMBOLS_FIELD_NUMBER: _ClassVar[int]
    STRATEGY_FIELD_NUMBER: _ClassVar[int]
    symbols: _containers.RepeatedScalarFieldContainer[str]
    strategy: str
    def __init__(self, symbols: _Optional[_Iterable[str]] = ..., strategy: _Optional[str] = ...) -> None: ...

class SubmitSignalRequest(_message.Message):
    __slots__ = ("signal",)
    SIGNAL_FIELD_NUMBER: _ClassVar[int]
    signal: _trading_pb2.Signal
    def __init__(self, signal: _Optional[_Union[_trading_pb2.Signal, _Mapping]] = ...) -> None: ...

class SubmitSignalResponse(_message.Message):
    __slots__ = ("accepted", "order_id", "message")
    ACCEPTED_FIELD_NUMBER: _ClassVar[int]
    ORDER_ID_FIELD_NUMBER: _ClassVar[int]
    MESSAGE_FIELD_NUMBER: _ClassVar[int]
    accepted: bool
    order_id: str
    message: str
    def __init__(self, accepted: _Optional[bool] = ..., order_id: _Optional[str] = ..., message: _Optional[str] = ...) -> None: ...
