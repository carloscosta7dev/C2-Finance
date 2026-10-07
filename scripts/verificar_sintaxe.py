#!/usr/bin/env python3
"""Verificador estrutural simples para os arquivos Java do projeto.

Nao substitui um compilador real, mas detecta delimitadores desbalanceados,
comentarios nao fechados e literais string/char/text block incompletos.
"""

import re
import sys
from pathlib import Path


def tokenizar_e_checar(caminho: Path) -> list[str]:
    texto = caminho.read_text(encoding="utf-8")
    pilha = []
    pares = {")": "(", "]": "[", "}": "{"}
    abrir = set(pares.values())
    fechar = set(pares)

    i = 0
    n = len(texto)
    linha = 1
    erros = []

    while i < n:
        c = texto[i]
        if c == "\n":
            linha += 1
            i += 1
            continue

        if c == "/" and i + 1 < n and texto[i + 1] == "/":
            while i < n and texto[i] != "\n":
                i += 1
            continue

        if c == "/" and i + 1 < n and texto[i + 1] == "*":
            fim = texto.find("*/", i + 2)
            if fim == -1:
                erros.append(f"linha {linha}: comentario de bloco nao fechado")
                break
            linha += texto.count("\n", i, fim)
            i = fim + 2
            continue

        if c == '"' and texto[i:i + 3] == '"""':
            inicio_busca = i + 3
            fim = -1
            while True:
                candidato = texto.find('"""', inicio_busca)
                if candidato == -1:
                    break
                barras = 0
                anterior = candidato - 1
                while anterior >= i and texto[anterior] == "\\":
                    barras += 1
                    anterior -= 1
                if barras % 2 == 0:
                    fim = candidato
                    break
                inicio_busca = candidato + 3
            if fim == -1:
                erros.append(f'linha {linha}: text block (""") nao fechado')
                break
            linha += texto.count("\n", i, fim)
            i = fim + 3
            continue

        if c == '"':
            j = i + 1
            while j < n:
                if texto[j] == '"':
                    break
                if texto[j] == "\\":
                    j += 2
                elif texto[j] == "\n":
                    erros.append(f"linha {linha}: string literal nao fechada antes de quebra de linha")
                    break
                else:
                    j += 1
            if j >= n:
                erros.append(f"linha {linha}: string literal nao fechada")
                i = n
                continue
            if texto[j] == '"':
                i = j + 1
            else:
                i = j
            continue

        if c == "'":
            j = i + 1
            while j < n and texto[j] != "'" and texto[j] != "\n":
                if texto[j] == "\\":
                    j += 2
                else:
                    j += 1
            if j >= n or texto[j] != "'":
                erros.append(f"linha {linha}: literal char nao fechado")
                i = j
                continue
            i = j + 1
            continue

        if c in abrir:
            pilha.append((c, linha))
        elif c in fechar:
            if not pilha:
                erros.append(f"linha {linha}: '{c}' sem abertura correspondente")
            else:
                aberto, linha_abertura = pilha.pop()
                if aberto != pares[c]:
                    erros.append(f"linha {linha}: '{c}' nao casa com '{aberto}' aberto na linha {linha_abertura}")

        i += 1

    if pilha:
        for aberto, linha_abertura in pilha:
            erros.append(f"'{aberto}' aberto na linha {linha_abertura} nunca foi fechado")

    return erros


def checar_nome_classe(caminho: Path) -> str | None:
    texto = caminho.read_text(encoding="utf-8")
    nome_esperado = caminho.stem
    padrao = re.compile(
        r"public\s+(?:final\s+|abstract\s+|sealed\s+)*(class|interface|enum|record)\s+(\w+)"
    )
    achados = padrao.findall(texto)
    nomes = [nome for _, nome in achados]
    if nome_esperado not in nomes:
        return f"nenhum tipo publico '{nome_esperado}' encontrado (encontrados: {nomes})"
    return None


def checar_package(caminho: Path, raiz_src: Path) -> str | None:
    texto = caminho.read_text(encoding="utf-8")
    match = re.search(r"^package\s+([\w.]+)\s*;", texto, re.MULTILINE)
    if not match:
        return "declaracao 'package' nao encontrada"
    pacote_declarado = match.group(1)
    caminho_relativo = caminho.parent.relative_to(raiz_src)
    pacote_esperado = ".".join(caminho_relativo.parts)
    if pacote_declarado != pacote_esperado:
        return f"package declarado '{pacote_declarado}' != esperado pelo caminho '{pacote_esperado}'"
    return None


def main() -> int:
    raiz = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(".")
    raiz_src = raiz / "src" / "main" / "java"
    arquivos = sorted(raiz_src.rglob("*.java"))
    if not arquivos:
        print("Nenhum arquivo .java encontrado em", raiz_src)
        return 1

    total_erros = 0
    for arquivo in arquivos:
        relativo = arquivo.relative_to(raiz)
        problemas = tokenizar_e_checar(arquivo)
        erro_classe = checar_nome_classe(arquivo)
        if erro_classe:
            problemas.append(erro_classe)
        erro_package = checar_package(arquivo, raiz_src)
        if erro_package:
            problemas.append(erro_package)

        if problemas:
            total_erros += len(problemas)
            print(f"[ERRO] {relativo}")
            for problema in problemas:
                print(f"    - {problema}")
        else:
            print(f"[OK] {relativo}")

    print(f"\n{len(arquivos)} arquivo(s) verificado(s), {total_erros} problema(s) encontrado(s).")
    return 1 if total_erros else 0


if __name__ == "__main__":
    sys.exit(main())