import { TestBed } from '@angular/core/testing';
import { ScoreRingComponent } from './score-ring.component';

describe('ScoreRingComponent', () => {
  it('affiche le score sur 100 avec la couleur de gravité', () => {
    const f = TestBed.createComponent(ScoreRingComponent);
    f.componentRef.setInput('score', 38);
    f.detectChanges();
    const el: HTMLElement = f.nativeElement;
    expect(el.querySelector('.valeur')!.textContent!.trim()).toBe('38');
    expect(el.querySelector('svg')!.getAttribute('aria-label')).toBe('Score 38 sur 100');
    expect(f.componentInstance.couleur()).toBe('var(--critique)');
  });
});
