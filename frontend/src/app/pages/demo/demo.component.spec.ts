import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { Meta } from '@angular/platform-browser';
import { DemoComponent, lignesCode } from './demo.component';

describe('DemoComponent', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient()] }));

  it('affiche les chiffres du vrai run, la ligne contradictoire surlignée et le commentaire de PR en tableau', () => {
    const f = TestBed.createComponent(DemoComponent);
    f.detectChanges();
    const el: HTMLElement = f.nativeElement;
    const chiffres = Array.from(el.querySelectorAll('[data-test="chiffre"] b')).map((b) => b.textContent!.trim());
    expect(chiffres[0]).toBe('9');
    expect(chiffres[1]).toMatch(/^100\s?%$/);
    expect(el.querySelector('.ligne.marque')).not.toBeNull();
    expect(Array.from(el.querySelectorAll('.ligne.marque')).some((l) => l.textContent!.includes('total > 50'))).toBeTrue();
    expect(el.querySelector('[data-test="bug"]')).not.toBeNull();

    f.componentInstance.onglet.set(2);
    f.detectChanges();
    const pr = el.querySelector('[data-test="commentaire-pr"]')!;
    expect(pr.querySelectorAll('table').length).toBe(2);
    expect(pr.textContent).toContain('proven (green)');
    expect(pr.querySelector('script')).toBeNull();
    expect(pr.querySelector('.pr-liste')!.textContent).toContain('AssertionError: 50.0 != 45.0');
    expect(TestBed.inject(Meta).getTag('name="description"')?.content ?? '').toContain('RevIA');
  });

  it('numérote les lignes et marque celles qui contiennent un extrait', () => {
    expect(lignesCode('a\nb > 50\n', ['> 50'])).toEqual([{ n: 1, texte: 'a', marque: false }, { n: 2, texte: 'b > 50', marque: true }]);
  });
});
