extends Node2D

var elapsed := 0.0

func _ready() -> void:
    set_process(true)
    queue_redraw()

func _process(delta: float) -> void:
    elapsed += delta
    queue_redraw()

func _draw() -> void:
    var size := get_viewport_rect().size
    draw_rect(Rect2(Vector2.ZERO, size), Color("#08090b"))
    draw_rect(Rect2(size.x * 0.12, size.y * 0.10, size.x * 0.76, size.y * 0.80), Color("#17191c"))
    draw_rect(Rect2(size.x * 0.18, size.y * 0.16, size.x * 0.64, size.y * 0.68), Color("#24272b"))

    for index in range(7):
        var y := size.y * (0.20 + float(index) * 0.09)
        draw_line(Vector2(size.x * 0.18, y), Vector2(size.x * 0.82, y), Color("#35393d"), 2.0)

    # Stable crimson region used by the Android screenshot assertion.
    draw_rect(Rect2(size.x * 0.25, size.y * 0.34, size.x * 0.50, size.y * 0.32), Color(0.78, 0.035, 0.09, 1.0))
    draw_rect(Rect2(size.x * 0.32, size.y * 0.42, size.x * 0.36, size.y * 0.16), Color(0.09, 0.012, 0.02, 1.0))
    draw_circle(Vector2(size.x * 0.50, size.y * 0.50), size.x * (0.035 + 0.002 * sin(elapsed)), Color(0.90, 0.06, 0.12, 1.0))
