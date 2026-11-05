import { Component, ChangeDetectionStrategy } from '@angular/core';
import { ThemeService } from './service/theme.service';

@Component({
    selector: 'app-root',
    templateUrl: './app.component.html',
    styleUrls: ['./app.component.scss'],
    changeDetection: ChangeDetectionStrategy.Eager,
    standalone: false
})
export class AppComponent {

    constructor(public themeService: ThemeService) { }

}
