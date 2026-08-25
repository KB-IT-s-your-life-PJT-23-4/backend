import argparse
import re
from pathlib import Path
from typing import Any

import yaml


HTTP_METHODS = {
    "get",
    "post",
    "put",
    "patch",
    "delete",
    "options",
    "head",
}
FRAMEWORK_PARAMETER_NAMES = {
    "authentication",
    "credentials",
    "details",
    "httpRequest",
    "principal",
    "requestUri",
}


def safe_definition_name(name: str) -> str:
    name = name.replace("«", "Of").replace("»", "")
    name = name.replace("[", "Of").replace("]", "")
    name = re.sub(r"[^A-Za-z0-9._-]+", "_", name)
    return re.sub(r"_+", "_", name).strip("_")


def rename_definitions(document: dict[str, Any]) -> None:
    definitions = document.get("definitions", {})
    renamed: dict[str, Any] = {}
    name_map: dict[str, str] = {}

    for old_name, schema in definitions.items():
        new_name = safe_definition_name(old_name)
        if new_name in renamed and old_name != new_name:
            raise ValueError(f"정의 이름 변환 충돌: {old_name} -> {new_name}")
        renamed[new_name] = schema
        name_map[old_name] = new_name

    document["definitions"] = renamed

    def update_refs(value: Any) -> None:
        if isinstance(value, dict):
            reference = value.get("$ref")
            if isinstance(reference, str) and reference.startswith("#/definitions/"):
                old_name = reference.removeprefix("#/definitions/")
                value["$ref"] = "#/definitions/" + name_map.get(
                    old_name,
                    safe_definition_name(old_name),
                )
            for child in value.values():
                update_refs(child)
        elif isinstance(value, list):
            for child in value:
                update_refs(child)

    update_refs(document)


def clean_operation_parameters(document: dict[str, Any]) -> None:
    for path_item in document.get("paths", {}).values():
        for method, operation in path_item.items():
            if method not in HTTP_METHODS or not isinstance(operation, dict):
                continue

            cleaned_parameters: list[dict[str, Any]] = []
            for parameter in operation.get("parameters", []):
                name = parameter.get("name")
                location = parameter.get("in")

                if name in FRAMEWORK_PARAMETER_NAMES:
                    continue

                if location != "body" and parameter.get("type") == "object":
                    continue

                if location == "formData" and "schema" in parameter:
                    parameter.pop("schema", None)
                    parameter["type"] = "string"

                cleaned_parameters.append(parameter)

            operation["parameters"] = cleaned_parameters


def remove_invalid_date_time_examples(value: Any) -> None:
    if isinstance(value, dict):
        if value.get("format") == "date-time":
            value.pop("example", None)
            value.pop("x-example", None)
        for child in value.values():
            remove_invalid_date_time_examples(child)
    elif isinstance(value, list):
        for child in value:
            remove_invalid_date_time_examples(child)


def validate_document(document: dict[str, Any]) -> None:
    definitions = set(document.get("definitions", {}))
    errors: list[str] = []

    def validate_refs(value: Any, location: str = "") -> None:
        if isinstance(value, dict):
            reference = value.get("$ref")
            if isinstance(reference, str) and reference.startswith("#/definitions/"):
                name = reference.removeprefix("#/definitions/")
                if name not in definitions:
                    errors.append(f"존재하지 않는 참조: {location} -> {reference}")
                if not re.fullmatch(r"#/definitions/[A-Za-z0-9._-]+", reference):
                    errors.append(f"유효하지 않은 참조 URI: {location} -> {reference}")
            for key, child in value.items():
                validate_refs(child, f"{location}/{key}")
        elif isinstance(value, list):
            for index, child in enumerate(value):
                validate_refs(child, f"{location}/{index}")

    validate_refs(document)

    for path, path_item in document.get("paths", {}).items():
        for method, operation in path_item.items():
            if method not in HTTP_METHODS or not isinstance(operation, dict):
                continue

            parameters = operation.get("parameters", [])
            body_count = sum(p.get("in") == "body" for p in parameters)
            form_count = sum(p.get("in") == "formData" for p in parameters)

            if body_count > 1:
                errors.append(f"body 파라미터 중복: {method.upper()} {path}")
            if body_count and form_count:
                errors.append(f"body/formData 동시 사용: {method.upper()} {path}")

            for parameter in parameters:
                location = parameter.get("in")
                if location != "body" and "type" not in parameter:
                    errors.append(
                        f"type 없는 파라미터: {method.upper()} {path} "
                        f"{parameter.get('name')}"
                    )
                if location != "body" and parameter.get("type") == "object":
                    errors.append(
                        f"허용되지 않는 object 파라미터: {method.upper()} {path} "
                        f"{parameter.get('name')}"
                    )

    if errors:
        raise ValueError("\n".join(errors))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()

    with args.input.open("r", encoding="utf-8") as source:
        document = yaml.safe_load(source)

    clean_operation_parameters(document)
    rename_definitions(document)
    remove_invalid_date_time_examples(document)
    validate_document(document)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="\n") as target:
        yaml.safe_dump(
            document,
            target,
            allow_unicode=True,
            sort_keys=False,
            width=120,
        )


if __name__ == "__main__":
    main()
