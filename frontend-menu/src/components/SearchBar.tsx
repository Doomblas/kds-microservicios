import { Search, X } from 'lucide-react'
import { useEffect, useRef } from 'react'

interface SearchBarProps {
  value: string
  onChange: (value: string) => void
}

const isMac =
  typeof navigator !== 'undefined' &&
  /Mac|iPhone|iPad/.test(navigator.platform)

export function SearchBar({ value, onChange }: SearchBarProps) {
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const handleShortcut = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        if (document.querySelector('[role="dialog"]')) {
          return
        }

        event.preventDefault()
        inputRef.current?.focus()
        inputRef.current?.select()
      }
    }

    window.addEventListener('keydown', handleShortcut)
    return () => window.removeEventListener('keydown', handleShortcut)
  }, [])

  return (
    <div className="search-field">
      <Search className="search-icon" size={19} aria-hidden="true" />
      <input
        ref={inputRef}
        type="search"
        className="search-input"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === 'Escape' && value) {
            onChange('')
          }
        }}
        placeholder="Buscar por nombre de producto…"
        aria-label="Buscar productos por nombre"
        aria-keyshortcuts="Control+K Meta+K"
        autoComplete="off"
      />
      {value ? (
        <button
          type="button"
          className="search-clear"
          onClick={() => {
            onChange('')
            inputRef.current?.focus()
          }}
          aria-label="Limpiar búsqueda"
        >
          <X size={16} aria-hidden="true" />
        </button>
      ) : (
        <kbd className="search-shortcut" aria-hidden="true">
          {isMac ? '⌘K' : 'Ctrl K'}
        </kbd>
      )}
    </div>
  )
}
