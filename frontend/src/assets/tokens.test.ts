import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const css = readFileSync(resolve(process.cwd(), 'src/assets/tokens.css'), 'utf8')

function tokenBlock(selector: string): string {
  const match = css.match(new RegExp(`${selector}\\s*\\{([\\s\\S]*?)\\n\\}`))
  if (!match) throw new Error(`Theme token block not found: ${selector}`)
  return match[1]
}

function tokenValue(block: string, name: string): string {
  const match = block.match(new RegExp(`${name}:\\s*(#[0-9a-fA-F]{6})`))
  if (!match) throw new Error(`Hex token not found: ${name}`)
  return match[1]
}

function relativeLuminance(hex: string): number {
  const channels = hex.match(/[a-f\d]{2}/gi)?.map((channel) => Number.parseInt(channel, 16) / 255)
  if (!channels || channels.length !== 3) throw new Error(`Invalid color: ${hex}`)

  return channels
    .map((channel) => channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4)
    .reduce((sum, channel, index) => sum + channel * [0.2126, 0.7152, 0.0722][index], 0)
}

function contrastRatio(foreground: string, background: string): number {
  const foregroundLuminance = relativeLuminance(foreground)
  const backgroundLuminance = relativeLuminance(background)
  return (Math.max(foregroundLuminance, backgroundLuminance) + 0.05)
    / (Math.min(foregroundLuminance, backgroundLuminance) + 0.05)
}

describe('theme text contrast tokens', () => {
  it('keeps light-theme reading text at WCAG AA on the main surfaces', () => {
    const theme = tokenBlock(':root')
    const backgrounds = [
      tokenValue(theme, '--surface-primary'),
      tokenValue(theme, '--surface-secondary'),
    ]

    for (const textToken of ['--text-primary', '--text-secondary', '--text-tertiary', '--text-quaternary']) {
      const foreground = tokenValue(theme, textToken)
      for (const background of backgrounds) {
        expect(contrastRatio(foreground, background), `${textToken} on ${background}`).toBeGreaterThanOrEqual(4.5)
      }
    }
  })

  it('keeps dark-theme reading text at WCAG AA across elevated surfaces', () => {
    const theme = tokenBlock('\\.dark')
    const backgrounds = [
      tokenValue(theme, '--surface-primary'),
      tokenValue(theme, '--surface-secondary'),
      tokenValue(theme, '--surface-tertiary'),
      tokenValue(theme, '--surface-elevated'),
      tokenValue(theme, '--surface-input'),
    ]

    for (const textToken of ['--text-primary', '--text-secondary', '--text-tertiary', '--text-quaternary']) {
      const foreground = tokenValue(theme, textToken)
      for (const background of backgrounds) {
        expect(contrastRatio(foreground, background), `${textToken} on ${background}`).toBeGreaterThanOrEqual(4.5)
      }
    }
  })

  it('keeps the dark navigation rail readable in both theme modes', () => {
    const lightTheme = tokenBlock(':root')
    const darkTheme = tokenBlock('\\.dark')
    const railBackground = tokenValue(lightTheme, '--surface-sidebar')

    for (const theme of [lightTheme, darkTheme]) {
      for (const textToken of ['--text-rail-primary', '--text-rail-secondary', '--text-rail-tertiary', '--text-rail-quaternary']) {
        const foreground = tokenValue(theme, textToken)
        expect(contrastRatio(foreground, railBackground), `${textToken} on rail`).toBeGreaterThanOrEqual(4.5)
      }
    }
  })
})
