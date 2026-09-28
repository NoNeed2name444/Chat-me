def test_application_import_smoke():
    from app.main import app
    assert app.title == "Medical Verifier"
    assert any(
        route.path == "/health"
        for route in app.routes
    )
